package com.company.demo.service.starrockssync;

import java.util.List;

public interface StarrocksToMainCrossDataSourceTableSynchronizer extends CrossDataSourceTableSynchronizer {

    List<ForeignColumnTable> provideForeignColumnTables();

    String prepareSelectSQLFromSource();

    record ForeignColumnTable(String columnName, String tableName, boolean nullable) {
    }

}
