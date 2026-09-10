package com.company.demo.service.starrockssync;

import java.util.List;

public interface StarrocksToMainTableSynchronizer extends TableSynchronizer {

    int[] provideTableColumnTypes();

    String provideTableColumns();

    List<ForeignColumnTable> provideForeignColumnTables();

    String prepareSelectSQLFromSource();

    record ForeignColumnTable(String tableName, boolean nullable) {
    }

}
