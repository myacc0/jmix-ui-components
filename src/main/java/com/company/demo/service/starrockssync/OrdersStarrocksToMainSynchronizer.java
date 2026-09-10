package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrdersStarrocksToMainSynchronizer implements StarrocksToMainTableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(OrdersStarrocksToMainSynchronizer.class);

    private final StarrocksSyncService starrocksSyncService;

    public OrdersStarrocksToMainSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    @Override
    public String provideTargetTable() {
        return "orders";
    }

    @Override
    public String provideSourceTable() {
        return "orders";
    }

    @Override
    public int[] provideTableColumnTypes() {
        return new int[] {
                Types.INTEGER, Types.OTHER, Types.DECIMAL, Types.DECIMAL, Types.DECIMAL, Types.INTEGER,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.TIMESTAMP, Types.LONGVARCHAR
        };
    }

    @Override
    public String provideTableColumns() {
        return "id, product_id, total_sum, sale_sum, delivery_sum, quantity, payment_method, "
                + "customer_phone, customer_email, customer_address, customer_name, order_date, notes";
    }

    @Override
    public List<ForeignColumnTable> provideForeignColumnTables() {
        return List.of(new ForeignColumnTable("products", false));
    }

    @Override
    public String prepareSelectSQLFromSource() {
        List<String> joinItems = new ArrayList<>();
        List<String> whereItems = new ArrayList<>();

        List<ForeignColumnTable> fTables = provideForeignColumnTables();
        for (ForeignColumnTable fTable : fTables) {
            if (fTable.nullable()) {
                joinItems.add("""
                    LEFT JOIN %s ON %s.id = %s.product_id
                    """.formatted(fTable.tableName(), provideSourceTable(), provideSourceTable()));

                whereItems.add("""
                    (%s.product_id IS NULL OR %s.id IS NOT NULL)
                    """.formatted(provideSourceTable(), fTable.tableName()));
            } else {
                joinItems.add("""
                    INNER JOIN %s ON %s.id = %s.product_id
                    """.formatted(fTable.tableName(), provideSourceTable(), provideSourceTable()));
            }
        }

        String joinSql = joinItems.stream().reduce("", (a, b) -> a + " " + b);
        String whereSql = whereItems.stream().reduce("", (a, b) -> a + " AND " + b);

        String selectSql = """
                SELECT %s.* FROM %s
                %s
                WHERE 1=1 AND %s
                """.formatted(provideSourceTable(), provideSourceTable(), joinSql, whereSql);
        log.debug("Orders starrocks -> main: select SQL: {}", selectSql);
        return selectSql;
    }

    @Override
    public SyncResult sync() {
        JdbcTemplate source = starrocksSyncService.getStarrocksStoreJdbcTemplate();
        JdbcTemplate target = starrocksSyncService.getMainStoreJdbcTemplate();

        int totalRows = Optional.ofNullable(
                target.queryForObject("SELECT count(*) FROM " + provideSourceTable(), Integer.class)).orElse(0);

        String upsertSql = "INSERT INTO " + provideTargetTable() + " (" + provideTableColumns() + ") VALUES ("
                + starrocksSyncService.placeholders(provideTableColumnTypes().length) + ") ON CONFLICT (id) DO UPDATE SET "
                + starrocksSyncService.updateAssignments(provideTableColumns());

        SyncResult result = starrocksSyncService.copy(source,
                prepareSelectSQLFromSource(),
                (rs, rowNum) -> new Object[]{
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("product_id")),
                        rs.getBigDecimal("total_sum"),
                        rs.getBigDecimal("sale_sum"),
                        rs.getBigDecimal("delivery_sum"),
                        rs.getInt("quantity"),
                        rs.getString("payment_method"),
                        rs.getString("customer_phone"),
                        rs.getString("customer_email"),
                        rs.getString("customer_address"),
                        rs.getString("customer_name"),
                        starrocksSyncService.localDateTime(rs, "order_date"),
                        rs.getString("notes")
                },
                batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes()), provideSyncBatchSize());

        log.info("Orders starrocks -> main: {}, ignoredNotConsistent: {}", result, Math.max(0, totalRows - result.written()));
        return result;
    }

}
