package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableColConfig;
import com.company.demo.entity.tablesync.TableSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MainToStarrocksDynamicSynchronizer extends DynamicTableSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(MainToStarrocksDynamicSynchronizer.class);

    public MainToStarrocksDynamicSynchronizer(StarrocksSyncService starrocksSyncService) {
        super(starrocksSyncService);
    }

    @Override
    public SyncResult sync(TableSynchronizer t) {
        TableColConfig cfg = parseTableColConfig(t);

        SyncResult result = starrocksSyncService.copyToStarrocks(
                t.getTargetTableName(), provideTableColumns(cfg.getColumns()), provideTableColumnTypes(cfg.getColumns()),
                "SELECT " + provideTableColumns(cfg.getColumns()) + " FROM " + t.getSourceTableName(),
                (rs, rowNum) -> provideTableRow(rs, cfg.getColumns()), t.getBatchSize());
        log.info("{} main -> starrocks: {}", t.getSourceTableName(), result);
        return result;
    }

}
