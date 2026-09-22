package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StarrocksToMainDynamicSynchronizer extends DynamicTableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(StarrocksToMainDynamicSynchronizer.class);

    public StarrocksToMainDynamicSynchronizer(StarrocksSyncService starrocksSyncService) {
        super(starrocksSyncService);
    }

    @Override
    public SyncResult sync(TableSynchronizer t) {
        TableColConfig cfg = parseTableColConfig(t);
        JdbcTemplate source = starrocksSyncService.getStarrocksStoreJdbcTemplate();
        JdbcTemplate target = starrocksSyncService.getMainStoreJdbcTemplate();

        int totalRows = Optional.ofNullable(
                source.queryForObject("SELECT count(*) FROM " + t.getSourceTableName(), Integer.class)).orElse(0);

        int deleted = deleteMissingInTarget(source, target, t, cfg.getColumns(), true);

        String upsertSql = "INSERT INTO " + t.getTargetTableName() + " (" + provideTableColumnsString(cfg.getColumns()) + ") VALUES ("
                + starrocksSyncService.placeholders(cfg.getColumns().size()) + ") ON CONFLICT ("
                + providePrimaryKeyColumnsString(cfg.getColumns()) + ") DO UPDATE SET "
                + starrocksSyncService.updateAssignments(provideTableColumns(cfg.getColumns()), providePrimaryKeyColumns(cfg.getColumns()));

        List<TableCol> selfReferencedCols = cfg.getColumns().stream().filter(TableCol::selfReferenced).toList();
        if (selfReferencedCols.isEmpty()) {
            SyncResult result = starrocksSyncService.copy(source,
                    prepareSelectSQLFromSource(t.getSourceTableName(), cfg),
                    (rs, rowNum) -> provideTableRow(rs, cfg.getColumns()),
                    batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes(cfg.getColumns())), t.getBatchSize());

            log.info("{} starrocks -> main: {}, ignoredByNotConsistent: {}, deleted: {}", t.getSourceTableName(), result,
                    Math.max(0, totalRows - result.written()), deleted);
            return result;
        }

        SelfReferenceSyncResult selfReferenceResult = syncSelfReferenced(source, target, t, cfg, upsertSql, selfReferencedCols);
        SyncResult result = selfReferenceResult.result();
        log.info("{} starrocks -> main: {}, ignoredByNotConsistent: {}, deleted: {}, selfReferencesRestored: {}, "
                        + "selfReferencesDangling: {}", t.getSourceTableName(), result,
                Math.max(0, totalRows - result.written()), deleted, selfReferenceResult.restored(),
                selfReferenceResult.dangling());
        return result;
    }

    /**
     * Two-stage copy of a table with self-referenced columns (e.g. {@code parent_id -> id}): a row may
     * reference a row that is written later, or never, so the foreign key of the target would reject a
     * one-pass upsert.
     * <ol>
     *     <li>Every row is upserted with its self-referenced columns set to {@code NULL}; the non-null
     *         references are remembered by the primary key of their row, and every written primary key
     *         is collected.</li>
     *     <li>A reference whose value is not among the written primary keys is dropped — its row stays
     *         with {@code NULL} there. The rest are restored with one
     *         {@code UPDATE ... SET col = ? WHERE pk = ?} per row, batched by {@code batchSize}.</li>
     * </ol>
     * A self-referenced column always references a single-column primary key.
     */
    private SelfReferenceSyncResult syncSelfReferenced(
            JdbcTemplate source,
            JdbcTemplate target,
            TableSynchronizer t,
            TableColConfig cfg,
            String upsertSql,
            List<TableCol> selfReferencedCols
    ) {
        List<TableCol> cols = cfg.getColumns();
        List<TableCol> primaryKeyCols = cols.stream().filter(TableCol::primaryKey).toList();
        if (primaryKeyCols.size() != 1) {
            throw new IllegalArgumentException(
                    "Self-referenced columns require a single-column primary key in table " + t.getTargetTableName());
        }
        TableCol primaryKeyCol = primaryKeyCols.get(0);
        int pkIndex = cols.indexOf(primaryKeyCol);
        int[] selfReferencedIndexes = selfReferencedCols.stream().mapToInt(cols::indexOf).toArray();

        // stage 1: upsert with the self-referenced columns cleared
        Set<Object> writtenKeys = new HashSet<>();
        List<Object[]> references = new ArrayList<>();
        SyncResult result = starrocksSyncService.copy(source,
                prepareSelectSQLFromSource(t.getSourceTableName(), cfg),
                (rs, rowNum) -> {
                    Object[] row = provideTableRow(rs, cols);
                    Object[] reference = new Object[selfReferencedIndexes.length + 1];
                    boolean hasReference = false;
                    for (int i = 0; i < selfReferencedIndexes.length; i++) {
                        reference[i] = row[selfReferencedIndexes[i]];
                        hasReference |= reference[i] != null;
                        row[selfReferencedIndexes[i]] = null;
                    }
                    if (hasReference) {
                        reference[selfReferencedIndexes.length] = row[pkIndex];
                        references.add(reference);
                    }
                    writtenKeys.add(starrocksSyncService.comparableValue(row[pkIndex]));
                    return row;
                },
                batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes(cols)), t.getBatchSize());

        // drop the references to rows that were not written
        int dangling = 0;
        List<Object[]> restorable = new ArrayList<>(references.size());
        for (Object[] reference : references) {
            boolean hasReference = false;
            for (int i = 0; i < selfReferencedIndexes.length; i++) {
                if (reference[i] != null && !writtenKeys.contains(starrocksSyncService.comparableValue(reference[i]))) {
                    reference[i] = null;
                    dangling++;
                }
                hasReference |= reference[i] != null;
            }
            if (hasReference) {
                restorable.add(reference);
            }
        }

        // stage 2: restore the references by primary key
        String updateSql = "UPDATE " + t.getTargetTableName() + " SET "
                + selfReferencedCols.stream().map(col -> col.name() + " = ?").collect(Collectors.joining(", "))
                + " WHERE " + primaryKeyCol.name() + " = ?";
        int[] updateTypes = new int[selfReferencedCols.size() + 1];
        for (int i = 0; i < selfReferencedCols.size(); i++) {
            updateTypes[i] = selfReferencedCols.get(i).sqlType();
        }
        updateTypes[selfReferencedCols.size()] = primaryKeyCol.sqlType();
        for (int from = 0; from < restorable.size(); from += t.getBatchSize()) {
            target.batchUpdate(updateSql,
                    restorable.subList(from, Math.min(from + t.getBatchSize(), restorable.size())), updateTypes);
        }
        return new SelfReferenceSyncResult(result, restorable.size(), dangling);
    }

    public String prepareSelectSQLFromSource(String rootTable, TableColConfig cfg) {
        List<String> joinItems = new ArrayList<>();
        List<String> whereItems = new ArrayList<>();

        List<FKColumn> fkItems = provideFKColumns(cfg.getColumns());
        for (FKColumn fk : fkItems) {
            if (fk.nullable()) {
                joinItems.add("""
                LEFT JOIN %s ON %s.%s = %s.%s""".formatted(
                        fk.fkTable, fk.fkTable, fk.fkTableCol, rootTable, fk.rootCol));

                whereItems.add("""
                (%s.%s IS NULL OR %s.%s IS NOT NULL)""".formatted(
                        rootTable, fk.rootCol, fk.fkTable(), fk.fkTableCol()));
            } else {
                joinItems.add("""
                INNER JOIN %s ON %s.%s = %s.%s""".formatted(
                        fk.fkTable, fk.fkTable, fk.fkTableCol, rootTable, fk.rootCol));
            }
        }

        String joinSql = String.join("\n", joinItems);
        String whereSql = String.join(" AND ", whereItems);

        String selectSql = """
            SELECT %s.* FROM %s
            %s
            WHERE %s
            """.formatted(
                        rootTable,
                        rootTable,
                        joinSql.isBlank() ? "" : joinSql,
                        whereSql.isBlank() ? "1=1" : whereSql
                )
                .replaceAll("(?m)^\\s*$\\n", "");

        log.debug("{} starrocks -> main: select SQL: {}", rootTable, selectSql);
        return selectSql;
    }

    private List<FKColumn> provideFKColumns(List<TableCol> cols) {
        return cols.stream()
                .filter(c -> StringUtils.hasText(c.foreignTable()) && StringUtils.hasText(c.foreignTableColumn()))
                .map(c -> new FKColumn(c.name(), c.foreignTable(), c.foreignTableColumn(), c.nullable()))
                .toList();
    }

    private List<String> provideTableColumns(List<TableCol> cols) {
        return cols.stream().map(TableCol::name).toList();
    }

    private record FKColumn(String rootCol, String fkTable, String fkTableCol, boolean nullable) {
    }

    /**
     * @param restored the rows whose self-references were restored at stage 2
     * @param dangling the self-references dropped because they point at a row that was not written
     */
    private record SelfReferenceSyncResult(SyncResult result, int restored, int dangling) {
    }
}
