package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.enums.tablesync.TableColJavaType;
import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MainToStarrocksDynamicSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(MainToStarrocksDynamicSynchronizer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StarrocksSyncService starrocksSyncService;

    public MainToStarrocksDynamicSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    public SyncResult sync(TableSynchronizer t) {
        TableColConfig cfg = parseTableColConfig(t);

        SyncResult result = starrocksSyncService.copyToStarrocks(
                t.getTargetTableName(), provideTableColumns(cfg.getColumns()), provideTableColumnTypes(cfg.getColumns()),
                "SELECT " + provideTableColumns(cfg.getColumns()) + " FROM " + t.getSourceTableName(),
                (rs, rowNum) -> provideTableRow(rs, cfg.getColumns()), t.getBatchSize());
        log.info("{} main -> starrocks: {}", t.getSourceTableName(), result);
        return result;
    }

    private TableColConfig parseTableColConfig(TableSynchronizer t) {
        return JsonUtils.parseConfig(t.getTableColConfig(), TableColConfig.class, objectMapper);
    }

    private Object[] provideTableRow(ResultSet rs, List<TableCol> cols) throws SQLException {
        Object[] row = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            TableCol col = cols.get(i);
            if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.STRING) {
                row[i] = rs.getString(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.UUID) {
                row[i] = starrocksSyncService.uuidText(rs, col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.BOOLEAN) {
                row[i] = rs.getBoolean(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.SHORT) {
                row[i] = rs.getShort(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.INTEGER) {
                row[i] = rs.getInt(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.LONG) {
                row[i] = rs.getLong(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.DOUBLE) {
                row[i] = rs.getDouble(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.BIGDECIMAL) {
                row[i] = rs.getBigDecimal(col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.LOCALDATE) {
                row[i] = starrocksSyncService.localDate(rs, col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.LOCALDATETIME) {
                row[i] = starrocksSyncService.localDateTime(rs, col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.OFFSETDATETIME) {
                row[i] = starrocksSyncService.offsetDateTime(rs, col.name());
            } else if (TableColJavaType.fromId(col.javaType()) == TableColJavaType.BYTEARRAY) {
                row[i] = starrocksSyncService.byteArray(rs, col.name());
            }
        }
        return row;
    }

    private int[] provideTableColumnTypes(List<TableCol> cols) {
        return cols.stream().mapToInt(TableCol::sqlType).toArray();
    }

    private String provideTableColumns(List<TableCol> cols) {
        return cols.stream().map(TableCol::name).collect(Collectors.joining(","));
    }

}
