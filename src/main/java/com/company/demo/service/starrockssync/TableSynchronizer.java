package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;

public interface TableSynchronizer {

    default int provideSyncBatchSize() {
        return 500;
    }

    String provideTableColumns();

    int[] provideTableColumnTypes();

    /** Copies {@code main} into {@code starrocks}. */
    SyncResult syncMainToStarrocks();

    /** Copies {@code starrocks} into {@code main}. */
    SyncResult syncStarrocksToMain();

}
