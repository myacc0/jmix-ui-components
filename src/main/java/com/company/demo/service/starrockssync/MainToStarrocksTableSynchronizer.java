package com.company.demo.service.starrockssync;

public interface MainToStarrocksTableSynchronizer extends TableSynchronizer {

    String provideTableColumns();

    int[] provideTableColumnTypes();

}
