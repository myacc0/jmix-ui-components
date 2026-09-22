package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableCol;
import com.company.demo.service.dq.DqDataSourceProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
            String columns,
            int[] columnTypes,
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
     * Deletes the rows of {@code targetTable} whose primary key is absent from {@code sourceTable}, so
     * a row removed at the source disappears from the target too. Works in either direction.
     * <p>
     * The two tables live in different stores and cannot be joined, so the source keys are read into
     * memory — primary key columns only. {@code keyMapper} reads every key column as its configured
     * java type, the same way the upsert reads it, so a key compares by value rather than by the text
     * each JDBC driver happens to render (see {@link #comparableValue}). The orphans are removed with one
     * {@code DELETE} per {@code batchSize} keys, as every StarRocks statement is its own load
     * transaction: a single-column key is matched with {@code WHERE pk IN (...)}, a composite one with
     * {@code WHERE (a = ? AND b = ?) OR ...}. Each key value is bound as read, with the {@code sqlType}
     * of its column — the value and JDBC type the upsert binds — so both stores accept it.
     * <p>
     * A self-referencing table (e.g. {@code parent_id -> id}) may keep rows at the source whose parent
     * was removed there. Before each {@code DELETE}, every {@code selfReferencedCols} column that still
     * points at a row of the chunk is set to {@code NULL}, so the foreign key of the target does not
     * reject the delete. Such a column always references a single-column primary key.
     *
     * @param primaryKeyCols     the primary key columns of the table, in a stable order
     * @param selfReferencedCols the columns referencing the primary key of the same table, whose
     *                           references to the deleted rows are cleared; empty to skip that step
     * @param keyMapper          maps a row selected with {@code primaryKeyCols} to its key values, in the
     *                           order of {@code primaryKeyCols}
     * @return the number of rows deleted from the target
     */
    public int deleteMissing(
            JdbcTemplate source,
            String sourceTable,
            JdbcTemplate target,
            String targetTable,
            List<TableCol> primaryKeyCols,
            List<TableCol> selfReferencedCols,
            RowMapper<Object[]> keyMapper,
            int batchSize
    ) {
        if (primaryKeyCols.isEmpty()) {
            throw new IllegalArgumentException("No primary key columns configured for table " + targetTable);
        }
        if (!selfReferencedCols.isEmpty() && primaryKeyCols.size() != 1) {
            throw new IllegalArgumentException(
                    "Self-referenced columns require a single-column primary key in table " + targetTable);
        }
        int[] pkColTypes = primaryKeyCols.stream().mapToInt(TableCol::sqlType).toArray();
        List<String> pkColNames = primaryKeyCols.stream().map(TableCol::name).toList();
        String primaryKeyColumnsString = String.join(", ", pkColNames);

        Set<List<Object>> sourceIds = new HashSet<>();
        source.query("SELECT " + primaryKeyColumnsString + " FROM " + sourceTable,
                (RowCallbackHandler) rs -> sourceIds.add(comparableKey(keyMapper.mapRow(rs, 0))));

        List<Object[]> orphanIds = new ArrayList<>();
        target.query("SELECT " + primaryKeyColumnsString + " FROM " + targetTable, (RowCallbackHandler) rs -> {
            Object[] id = keyMapper.mapRow(rs, 0);
            if (!sourceIds.contains(comparableKey(id))) {
                orphanIds.add(id);
            }
        });

        for (int from = 0; from < orphanIds.size(); from += batchSize) {
            List<Object[]> chunk = orphanIds.subList(from, Math.min(from + batchSize, orphanIds.size()));
            Object[] args = new Object[chunk.size() * pkColTypes.length];
            int[] types = new int[args.length];
            for (int i = 0; i < chunk.size(); i++) {
                System.arraycopy(chunk.get(i), 0, args, i * pkColTypes.length, pkColTypes.length);
                System.arraycopy(pkColTypes, 0, types, i * pkColTypes.length, pkColTypes.length);
            }
            for (TableCol selfReferencedCol : selfReferencedCols) {
                target.update("UPDATE " + targetTable + " SET " + selfReferencedCol.name() + " = NULL WHERE "
                        + keyCondition(List.of(selfReferencedCol.name()), chunk.size()), args, types);
            }
            target.update("DELETE FROM " + targetTable + " WHERE " + keyCondition(pkColNames, chunk.size()),
                    args, types);
        }
        return orphanIds.size();
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
            if (buffer.size() >= batchSize) {
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
    public String updateAssignments(List<String> columns, List<String> primaryKeyColumns) {
        return columns
                .stream()
                .filter(column -> !primaryKeyColumns.contains(column))
                .map(column -> column + " = EXCLUDED." + column)
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    public String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    /**
     * Writes a chunk as ONE multi-row {@code INSERT}. StarRocks turns every statement into its own
     * load transaction, so a JDBC batch of single-row inserts would cost one load per row; a single
     * statement carrying the whole chunk costs one. On a PRIMARY KEY table the insert is an upsert.
     */
    private void insertMultiRow(
            JdbcTemplate target,
            String table,
            String columns,
            int[] columnTypes,
            List<Object[]> rows
    ) {
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

    /** The key values in a form whose {@code equals}/{@code hashCode} match, see {@link #comparableValue}. */
    private List<Object> comparableKey(Object[] key) {
        List<Object> comparable = new ArrayList<>(key.length);
        for (Object value : key) {
            comparable.add(comparableValue(value));
        }
        return comparable;
    }

    /**
     * A key value in a form whose {@code equals}/{@code hashCode} match equal values read from either
     * store. Most java types already compare by value; the exceptions are normalized:
     * <ul>
     *     <li>{@link BigDecimal} — {@code equals} is scale-sensitive and the stores keep different
     *         scales ({@code 1.50} vs {@code 1.5}), so trailing zeros are stripped;</li>
     *     <li>{@link OffsetDateTime} — {@code equals} also compares the offset, so the instant is used;</li>
     *     <li>{@code byte[]} — {@code equals} is identity, so the bytes are wrapped in a
     *         {@link ByteBuffer}, which compares by content.</li>
     * </ul>
     */
    public Object comparableValue(Object value) {
        return switch (value) {
            case BigDecimal decimal -> decimal.stripTrailingZeros();
            case OffsetDateTime dateTime -> dateTime.toInstant();
            case byte[] bytes -> ByteBuffer.wrap(bytes);
            case null, default -> value;
        };
    }

    /**
     * {@code id IN (?, ?)} for a single-column key; {@code (a = ? AND b = ?) OR (a = ? AND b = ?)} for a
     * composite one, since StarRocks does not accept a row-value {@code IN}.
     */
    private String keyCondition(List<String> pkColNames, int keyCount) {
        if (pkColNames.size() == 1) {
            return pkColNames.get(0) + " IN (" + placeholders(keyCount) + ")";
        }
        String keyMatch = pkColNames.stream()
                .map(column -> column + " = ?")
                .collect(Collectors.joining(" AND ", "(", ")"));
        return String.join(" OR ", Collections.nCopies(keyCount, keyMatch));
    }
}
