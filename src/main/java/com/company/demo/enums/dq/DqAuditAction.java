package com.company.demo.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqAuditAction implements EnumClass<String> {

    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    ACTIVATE("activate"),
    DEACTIVATE("deactivate");

    private final String id;

    DqAuditAction(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqAuditAction fromId(String id) {
        for (DqAuditAction at : DqAuditAction.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}