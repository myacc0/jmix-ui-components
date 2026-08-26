package com.company.demo.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqDimension implements EnumClass<String> {

    COMPLETENESS("completeness"),
    ACCURACY("accuracy"),
    UNIQUENESS("uniqueness"),
    VALIDITY("validity"),
    TIMELINESS("timeliness"),
    CONSISTENCY("consistency");

    private final String id;

    DqDimension(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqDimension fromId(String id) {
        for (DqDimension at : DqDimension.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}