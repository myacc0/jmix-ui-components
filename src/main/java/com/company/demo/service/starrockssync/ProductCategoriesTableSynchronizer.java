package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Types;

@Service
public class ProductCategoriesTableSynchronizer implements TableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(ProductCategoriesTableSynchronizer.class);

    private final StarrocksSyncService starrocksSyncService;

    public ProductCategoriesTableSynchronizer(StarrocksSyncService starrocksSyncService) {
        this.starrocksSyncService = starrocksSyncService;
    }

    @Override
    public String provideTableColumns() {
        return "id, version, name, description, parent_id, created_at, updated_at";
    }

    @Override
    public int[] provideTableColumnTypes() {
        return new int[] {
                Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.LONGVARCHAR, Types.VARCHAR,
                Types.TIMESTAMP, Types.TIMESTAMP
        };
    }

    @Override
    public SyncResult syncMainToStarrocks() {
        SyncResult result = starrocksSyncService.copyToStarrocks(
                "product_categories", provideTableColumns(), provideTableColumnTypes(),
                "SELECT id, version, name, description, parent_id, created_at, updated_at"
                        + " FROM demo_product_category",
                (rs, rowNum) -> new Object[]{
                        starrocksSyncService.uuidText(rs, "id"),
                        rs.getInt("version"),
                        rs.getString("name"),
                        rs.getString("description"),
                        starrocksSyncService.uuidText(rs, "parent_id"),
                        starrocksSyncService.localDateTime(rs, "created_at"),
                        starrocksSyncService.localDateTime(rs, "updated_at")
                }, provideSyncBatchSize());
        log.info("Product categories main -> starrocks: {}", result);
        return result;
    }

    @Override
    public SyncResult syncStarrocksToMain() {
        throw new UnsupportedOperationException();
    }
}
