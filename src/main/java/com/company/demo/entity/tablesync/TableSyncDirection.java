package com.company.demo.entity.tablesync;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum TableSyncDirection implements EnumClass<String> {

    MAIN_TO_STARROCKS("main_to_starrocks"),
    STARROCKS_TO_MAIN("starrocks_to_main");

    private final String id;

    TableSyncDirection(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static TableSyncDirection fromId(String id) {
        for (TableSyncDirection at : TableSyncDirection.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}