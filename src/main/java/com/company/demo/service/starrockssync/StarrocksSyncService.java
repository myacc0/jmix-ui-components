package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.service.DqDataSourceProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Mirrors tables between the MAIN store (PostgreSQL) and the StarRocks store, for the Quartz jobs
 * in {@code com.company.demo.jobs}.
 * <p>
 * The copy runs over {@link JdbcTemplate} rather than {@code DataManager} on purpose:
 * <ul>
 *     <li>StarRocks has no mapped Jmix entities — its tables were unmapped when the product CRUD
 *         moved to the main store — so there is nothing for {@code DataManager} to load or save
 *         on that side.</li>
 *     <li>A mirror must preserve the source ids and the source {@code created_at}/{@code updated_at}
 *         verbatim, which the entity lifecycle callbacks ({@code @PrePersist}/{@code @PreUpdate})
 *         would overwrite.</li>
 *     <li>It is a bulk column-to-column copy, so a per-row "load, compare, save" round trip buys
 *         nothing.</li>
 * </ul>
 * "Update or insert" is expressed differently per target: a StarRocks PRIMARY KEY table treats a
 * plain {@code INSERT} as an upsert, while PostgreSQL needs an explicit
 * {@code ON CONFLICT (id) DO UPDATE}.
 * <p>
 * No transaction spans a whole sync. StarRocks commits every load statement on its own and cannot
 * roll one back later, and every statement here is idempotent, so a run that dies half way leaves
 * the target consistent and the next run finishes the job.
 */
@Service
public class StarrocksSyncService {

    /** Rows sent to the target per statement. */
    private static final int BATCH_SIZE = 500;

    private final DqDataSourceProvider dataSourceProvider;

    public StarrocksSyncService(DqDataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    public JdbcTemplate getMainStoreJdbcTemplate() {
        return dataSourceProvider.getJdbcTemplate("main");
    }

    public JdbcTemplate getStarrocksStoreJdbcTemplate() {
        return dataSourceProvider.getJdbcTemplate("starrocks");
    }

    public SyncResult copyToStarrocks(
            String targetTable,
            String columns, int[] columnTypes,
            String selectSql,
            RowMapper<Object[]> mapper,
            int batchSize
    ) {
        JdbcTemplate source = getMainStoreJdbcTemplate();
        JdbcTemplate target = getStarrocksStoreJdbcTemplate();
        return copy(source, selectSql, mapper,
                batch -> insertMultiRow(target, targetTable, columns, columnTypes, batch), batchSize);
    }

    /**
     * Streams the source query through {@code mapper} and hands the mapped rows to {@code flush} in
     * chunks of {@code batchSize}, so a large table never lands in memory as a whole. A mapper
     * returning {@code null} drops the row from the copy and it is counted as skipped.
     */
    public SyncResult copy(
            JdbcTemplate source,
            String selectSql,
            RowMapper<Object[]> mapper,
            Consumer<List<Object[]>> flush,
            int batchSize
    ) {
        List<Object[]> buffer = new ArrayList<>(batchSize);
        // [0] = rows read from the source, [1] = rows written to the target
        int[] counters = new int[2];
        source.query(selectSql, (RowCallbackHandler) rs -> {
            Object[] row = mapper.mapRow(rs, counters[0]++);
            if (row == null) {
                return;
            }
            buffer.add(row);
            if (buffer.size() >= BATCH_SIZE) {
                flush.accept(buffer);
                counters[1] += buffer.size();
                buffer.clear();
            }
        });
        if (!buffer.isEmpty()) {
            flush.accept(buffer);
            counters[1] += buffer.size();
        }
        return new SyncResult(counters[0], counters[1], counters[0] - counters[1]);
    }

    /** {@code "a, b, c"} -> {@code "a = EXCLUDED.a, b = EXCLUDED.b, c = EXCLUDED.c"}, minus the id. */
    public String updateAssignments(String columns) {
        return Arrays.stream(columns.split(","))
                .map(String::trim)
                .filter(column -> !"id".equalsIgnoreCase(column))
                .map(column -> column + " = EXCLUDED." + column)
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    public String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /**
     * The StarRocks mirror stores a uuid as {@code VARCHAR(36)}; pgjdbc hands back a
     * {@link UUID} for a {@code uuid} column, so the text form is taken from the value itself.
     */
    @Nullable
    public String uuidText(ResultSet rs, String column) throws SQLException {
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
    public LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    /**
     * Writes a chunk as ONE multi-row {@code INSERT}. StarRocks turns every statement into its own
     * load transaction, so a JDBC batch of single-row inserts would cost one load per row; a single
     * statement carrying the whole chunk costs one. On a PRIMARY KEY table the insert is an upsert.
     */
    private void insertMultiRow(JdbcTemplate target, String table, String columns, int[] columnTypes,
                                List<Object[]> rows) {
        String rowPlaceholders = "(" + placeholders(columnTypes.length) + ")";
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES "
                + String.join(", ", Collections.nCopies(rows.size(), rowPlaceholders));

        Object[] args = new Object[rows.size() * columnTypes.length];
        int[] types = new int[args.length];
        for (int i = 0; i < rows.size(); i++) {
            System.arraycopy(rows.get(i), 0, args, i * columnTypes.length, columnTypes.length);
            System.arraycopy(columnTypes, 0, types, i * columnTypes.length, columnTypes.length);
        }
        target.update(sql, args, types);
    }
}
