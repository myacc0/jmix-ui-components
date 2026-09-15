package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;
import com.company.demo.entity.tablesync.TableSyncDirection;
import com.company.demo.entity.tablesync.TableSynchronizer;
import io.jmix.core.UnconstrainedDataManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TableDynamicSynchronizer {
    private final UnconstrainedDataManager dataManager;
    private final MainToStarrocksDynamicSynchronizer mainToStarrocksDynamicSynchronizer;
    private final StarrocksToMainDynamicSynchronizer starrocksToMainDynamicSynchronizer;

    public TableDynamicSynchronizer(
            UnconstrainedDataManager dataManager,
            MainToStarrocksDynamicSynchronizer mainToStarrocksDynamicSynchronizer,
            StarrocksToMainDynamicSynchronizer starrocksToMainDynamicSynchronizer
    ) {
        this.dataManager = dataManager;
        this.mainToStarrocksDynamicSynchronizer = mainToStarrocksDynamicSynchronizer;
        this.starrocksToMainDynamicSynchronizer = starrocksToMainDynamicSynchronizer;
    }

    public SyncResult sync(UUID id) {
        TableSynchronizer t = loadTableSynchronizer(id);
        if (TableSyncDirection.MAIN_TO_STARROCKS == t.getDirection()) {
            return mainToStarrocksDynamicSynchronizer.sync(t);
        } else if (TableSyncDirection.STARROCKS_TO_MAIN == t.getDirection()) {
            return starrocksToMainDynamicSynchronizer.sync(t);
        }
        throw new IllegalArgumentException("Unsupported direction: " + t.getDirection());
    }

    private TableSynchronizer loadTableSynchronizer(UUID id) {
        return dataManager.load(TableSynchronizer.class).id(id).one();
    }
}
