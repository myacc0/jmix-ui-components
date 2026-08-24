package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum OperationServiceType implements EnumClass<String> {

    CONSUMER("consumer"),
    AUTO("auto"),
    MORTAGE("mortgage");

    private final String id;

    OperationServiceType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OperationServiceType fromId(String id) {
        for (OperationServiceType at : OperationServiceType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}