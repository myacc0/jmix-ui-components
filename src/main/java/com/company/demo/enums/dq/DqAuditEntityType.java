package com.company.demo.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqAuditEntityType implements EnumClass<String> {

    RULE("rule"),
    ISSUE("issue");

    private final String id;

    DqAuditEntityType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqAuditEntityType fromId(String id) {
        for (DqAuditEntityType at : DqAuditEntityType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}