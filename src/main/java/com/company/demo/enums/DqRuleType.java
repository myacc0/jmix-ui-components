package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqRuleType implements EnumClass<String> {

    NOT_NULL("not_null"),
    RANGE_NUMBER("range_number"),
    RANGE_DATE("range_date"),
    REGEX("regex"),
    UNIQUENESS("uniqueness"),
    REFERENTIAL("referential"),
    CUSTOM_SQL("custom_sql"),
    CROSS_SOURCE_AGGREGATED("cross_source_aggregated");

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