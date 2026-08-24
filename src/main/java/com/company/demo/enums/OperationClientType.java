package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum OperationClientType implements EnumClass<String> {

    INDIVIDUAL("individual"),
    COMPANY("company");

    private final String id;

    OperationClientType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OperationClientType fromId(String id) {
        for (OperationClientType at : OperationClientType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}