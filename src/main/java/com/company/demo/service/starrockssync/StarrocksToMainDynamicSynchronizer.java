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
import java.util.List;
import java.util.Optional;

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

        SyncResult result = starrocksSyncService.copy(source,
                prepareSelectSQLFromSource(t.getSourceTableName(), cfg),
                (rs, rowNum) -> provideTableRow(rs, cfg.getColumns()),
                batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes(cfg.getColumns())), t.getBatchSize());

        log.info("{} starrocks -> main: {}, ignoredByNotConsistent: {}, deleted: {}", t.getSourceTableName(), result,
                Math.max(0, totalRows - result.written()), deleted);
        return result;
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
}
