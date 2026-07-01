package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqRuleType implements EnumClass<String> {

    NOT_NULL("not_null"),
    RANGE("range"),
    REGEX("regex"),
    UNIQUENESS("uniqueness"),
    REFERENTIAL("referential"),
    CUSTOM_SQL("custom_sql");

    private final String id;

    DqRuleType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqRuleType fromId(String id) {
        for (DqRuleType at : DqRuleType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}