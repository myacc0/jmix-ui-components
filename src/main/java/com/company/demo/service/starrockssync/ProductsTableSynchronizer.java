package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Types;

@Service
public class ProductsTableSynchronizer implements TableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(ProductsTableSynchronizer.class);

    private final StarrocksSyncService starrocksSyncService;

    public ProductsTableSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    @Override
    public String provideTableColumns() {
        return "id, name, description, price, sale, category_id, quantity, created_at, updated_at";
    }

    @Override
    public int[] provideTableColumnTypes() {
        return new int[] {
                Types.VARCHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.DECIMAL, Types.DECIMAL,
                Types.VARCHAR, Types.INTEGER, Types.TIMESTAMP, Types.TIMESTAMP
        };
    }

    @Override
    public SyncResult syncMainToStarrocks() {
        SyncResult result = starrocksSyncService.copyToStarrocks(
                "products", provideTableColumns(), provideTableColumnTypes(),
                "SELECT id, name, description, price, sale, category_id, quantity, created_at, updated_at"
                        + " FROM products",
                (rs, rowNum) -> new Object[]{
                        starrocksSyncService.uuidText(rs, "id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("sale"),
                        starrocksSyncService.uuidText(rs, "category_id"),
                        rs.getInt("quantity"),
                        starrocksSyncService.localDateTime(rs, "created_at"),
                        starrocksSyncService.localDateTime(rs, "updated_at")
                }, provideSyncBatchSize());
        log.info("Products main -> starrocks: {}", result);
        return result;
    }

    @Override
    public SyncResult syncStarrocksToMain() {
        throw new UnsupportedOperationException();
    }
}
