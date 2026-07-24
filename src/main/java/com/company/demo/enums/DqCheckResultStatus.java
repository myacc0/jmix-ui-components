package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqCheckResultStatus implements EnumClass<String> {

    PASSED("passed"),
    FAILED("failed"),
    WARNING("warning"),
    SKIPPED("skipped");

    private final String id;

    DqCheckResultStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqCheckResultStatus fromId(String id) {
        for (DqCheckResultStatus at : DqCheckResultStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}