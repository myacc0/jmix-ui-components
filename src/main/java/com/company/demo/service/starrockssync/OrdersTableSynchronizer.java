package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.*;

@Service
public class OrdersTableSynchronizer implements TableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(OrdersTableSynchronizer.class);

    private final StarrocksSyncService starrocksSyncService;

    public OrdersTableSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    @Override
    public String provideTableColumns() {
        return "id, product_id, total_sum, sale_sum, delivery_sum, quantity, payment_method, "
                + "customer_phone, customer_email, customer_address, customer_name, order_date, notes";
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
    public SyncResult syncMainToStarrocks() {
        throw new UnsupportedOperationException();
    }

    /**
     * Copies {@code starrocks.orders} into {@code main.orders}.
     * <p>
     * {@code main.orders.product_id} carries a real foreign key to {@code main.products}, which
     * StarRocks does not have. A StarRocks order pointing at a product the main store does not
     * hold would fail the whole batch, so such rows are skipped and reported instead — run the
     * product sync (or fix the source row) and the next run picks them up.
     */
    @Override
    public SyncResult syncStarrocksToMain() {
        JdbcTemplate source = starrocksSyncService.getStarrocksStoreJdbcTemplate();
        JdbcTemplate target = starrocksSyncService.getMainStoreJdbcTemplate();

        Set<String> knownProductIds = new HashSet<>(target.queryForList("SELECT id FROM products", String.class));
        List<Integer> skippedOrderIds = new ArrayList<>();

        String upsertSql = "INSERT INTO orders (" + provideTableColumns() + ") VALUES ("
                + starrocksSyncService.placeholders(provideTableColumnTypes().length) + ") ON CONFLICT (id) DO UPDATE SET "
                + starrocksSyncService.updateAssignments(provideTableColumns());

        SyncResult result = starrocksSyncService.copy(source,
                "SELECT " + provideTableColumns() + " FROM orders",
                (rs, rowNum) -> {
                    int orderId = rs.getInt("id");
                    String productId = rs.getString("product_id");
                    if (productId == null || !knownProductIds.contains(productId)) {
                        skippedOrderIds.add(orderId);
                        return null;
                    }
                    return new Object[]{
                            orderId,
                            UUID.fromString(productId),
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
                    };
                },
                batch -> target.batchUpdate(upsertSql, batch, provideTableColumnTypes()), provideSyncBatchSize());

        if (!skippedOrderIds.isEmpty()) {
            log.warn("Orders starrocks -> main: skipped {} order(s) whose product is missing in the main store: {}",
                    skippedOrderIds.size(), skippedOrderIds);
        }
        log.info("Orders starrocks -> main: {}", result);
        return result;
    }

}
