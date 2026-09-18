package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import com.company.demo.enums.tablesync.TableColJavaType;
import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
     * Deletes the target rows whose primary key no longer exists at the source; run it before the upsert.
     *
     * @return the number of rows deleted from the target
     */
    protected int deleteMissingInTarget(JdbcTemplate source, JdbcTemplate target, TableSynchronizer t, List<TableCol> cols) {
        List<TableCol> primaryKeyCols = cols.stream().filter(TableCol::primaryKey).toList();
        return starrocksSyncService.deleteMissing(
                source,
                t.getSourceTableName(),
                target,
                t.getTargetTableName(),
                primaryKeyCols,
                (rs, rowNum) -> provideTableRow(rs, primaryKeyCols),
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
                case UUID -> uuidText(rs, col.name());
                case BOOLEAN -> rs.getObject(col.name(), Boolean.class);
                case SHORT -> rs.getObject(col.name(), Short.class);
                case INTEGER -> rs.getObject(col.name(), Integer.class);
                case LONG -> rs.getObject(col.name(), Long.class);
                case DOUBLE -> rs.getObject(col.name(), Double.class);
                case BIGDECIMAL -> rs.getBigDecimal(col.name());
                case LOCALDATE -> localDate(rs, col.name());
                case LOCALDATETIME -> localDateTime(rs, col.name());
                case OFFSETDATETIME -> offsetDateTime(rs, col.name());
                case BYTEARRAY -> byteArray(rs, col.name());
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

    /**
     * The StarRocks mirror stores a uuid as {@code VARCHAR(36)}; pgjdbc hands back a
     * {@link UUID} for a {@code uuid} column, so the text form is taken from the value itself.
     */
    @Nullable
    private String uuidText(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }

    /**
     * Reads a timestamp as a zone-free {@link LocalDateTime}.
     * <p>
     * Both columns are wall-clock types — PostgreSQL {@code timestamp} and StarRocks
     * {@code DATETIME} — and neither carries a zone, so the mirror must copy the value verbatim.
     * {@link ResultSet#getTimestamp} would not: it resolves the value through a calendar, and the
     * {@code serverTimezone=UTC} of the StarRocks JDBC url then shifts it by the JVM's offset in
     * each direction, leaving the two stores showing different clock times for the same row.
     */
    @Nullable
    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    @Nullable
    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    @Nullable
    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    @Nullable
    private byte[] byteArray(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, byte[].class);
    }
}
