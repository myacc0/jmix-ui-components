package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.enums.tablesync.TableColJavaType;
import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DynamicTableSynchronizer {
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected final StarrocksSyncService starrocksSyncService;

    public DynamicTableSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    public abstract SyncResult sync(TableSynchronizer t);

    protected TableColConfig parseTableColConfig(TableSynchronizer t) {
        return JsonUtils.parseConfig(t.getTableColConfig(), TableColConfig.class, objectMapper);
    }

    /**
     * Deletes the target rows whose id no longer exists at the source; run it before the upsert.
     *
     * @return the number of rows deleted from the target
     */
    protected int deleteMissingInTarget(JdbcTemplate source, JdbcTemplate target, TableSynchronizer t, List<TableCol> cols) {
        return starrocksSyncService.deleteMissing(
                source,
                t.getSourceTableName(),
                target,
                t.getTargetTableName(),
                cols,
                t.getBatchSize());
    }

    protected Object[] provideTableRow(ResultSet rs, List<TableCol> cols) throws SQLException {
        Object[] row = new Object[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            TableCol col = cols.get(i);
            TableColJavaType javaType = TableColJavaType.fromId(col.javaType());
            if (javaType == null) {
                throw new IllegalStateException(
                        "Unsupported javaType '" + col.javaType() + "' of column '" + col.name() + "'");
            }
            row[i] = switch (javaType) {
                case STRING -> rs.getString(col.name());
                case UUID -> starrocksSyncService.uuidText(rs, col.name());
                case BOOLEAN -> rs.getObject(col.name(), Boolean.class);
                case SHORT -> rs.getObject(col.name(), Short.class);
                case INTEGER -> rs.getObject(col.name(), Integer.class);
                case LONG -> rs.getObject(col.name(), Long.class);
                case DOUBLE -> rs.getObject(col.name(), Double.class);
                case BIGDECIMAL -> rs.getBigDecimal(col.name());
                case LOCALDATE -> starrocksSyncService.localDate(rs, col.name());
                case LOCALDATETIME -> starrocksSyncService.localDateTime(rs, col.name());
                case OFFSETDATETIME -> starrocksSyncService.offsetDateTime(rs, col.name());
                case BYTEARRAY -> starrocksSyncService.byteArray(rs, col.name());
            };
        }
        return row;
    }

    protected int[] provideTableColumnTypes(List<TableCol> cols) {
        return cols.stream().mapToInt(TableCol::sqlType).toArray();
    }

    protected String provideTableColumnsString(List<TableCol> cols) {
        return cols.stream().map(TableCol::name).collect(Collectors.joining(","));
    }

    protected List<String> providePrimaryKeyColumns(List<TableCol> cols) {
        return cols.stream().filter(TableCol::primaryKey).map(TableCol::name).toList();
    }

    protected String providePrimaryKeyColumnsString(List<TableCol> cols) {
        return String.join(", ", providePrimaryKeyColumns(cols));
    }
}
