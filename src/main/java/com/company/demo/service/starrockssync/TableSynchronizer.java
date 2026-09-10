package com.company.demo.service.starrockssync;

import com.company.demo.dto.starrockssync.SyncResult;

public interface TableSynchronizer {

    default int provideSyncBatchSize() {
        return 500;
    }

    String provideTargetTable();

    String provideSourceTable();

    SyncResult sync();

}
