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

        String upsertSql = "INSERT INTO " + t.getTargetTableName() + " (" + provideTableColumns(cfg.getColumns()) + ") VALUES ("
                + starrocksSyncService.placeholders(provideTableColumnTypes(cfg.getColumns()).length) + ") ON CONFLICT (id) DO UPDATE SET "
                + starrocksSyncService.updateAssignments(provideTableColumns(cfg.getColumns()));

        SyncResult result = starrocksSyncService.copy(source,
                prepareSelectSQLFromSource(t.getSourceTableName(), cfg),
                (rs, rowNum) -> provideTableRow(rs, cfg.getColumns()),
                batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes(cfg.getColumns())), t.getBatchSize());

        log.info("{} starrocks -> main: {}, ignoredByNotConsistent: {}", t.getSourceTableName(), result, Math.max(0, totalRows - result.written()));
        return result;
    }

    public String prepareSelectSQLFromSource(String sourceTableName, TableColConfig cfg) {
        List<String> joinItems = new ArrayList<>();
        List<String> whereItems = new ArrayList<>();

        List<ForeignColumnTable> fTables = provideForeignColumnTables(cfg.getColumns());
        for (ForeignColumnTable fTable : fTables) {
            if (fTable.nullable()) {
                joinItems.add("""
                LEFT JOIN %s ON %s.id = %s.%s""".formatted(
                        fTable.tableName(), fTable.tableName(), sourceTableName, fTable.columnName()));

                whereItems.add("""
                (%s.%s IS NULL OR %s.id IS NOT NULL)""".formatted(
                        sourceTableName, fTable.columnName(), fTable.tableName()));
            } else {
                joinItems.add("""
                INNER JOIN %s ON %s.id = %s.%s""".formatted(
                        fTable.tableName(), fTable.tableName(), sourceTableName, fTable.columnName()));
            }
        }

        String joinSql = String.join("\n", joinItems);
        String whereSql = String.join(" AND ", whereItems);

        String selectSql = """
            SELECT %s.* FROM %s
            %s
            WHERE %s
            """.formatted(
                        sourceTableName,
                        sourceTableName,
                        joinSql.isBlank() ? "" : joinSql,
                        whereSql.isBlank() ? "1=1" : whereSql
                )
                .replaceAll("(?m)^\\s*$\\n", "");

        log.debug("{} starrocks -> main: select SQL: {}", sourceTableName, selectSql);
        return selectSql;
    }

    private List<ForeignColumnTable> provideForeignColumnTables(List<TableCol> cols) {
        return cols.stream()
                .filter(c -> StringUtils.hasText(c.foreignTable()))
                .map(c -> new ForeignColumnTable(c.name(), c.foreignTable(), c.nullable()))
                .toList();
    }

    private record ForeignColumnTable(String columnName, String tableName, boolean nullable) {
    }
}
