package com.company.demo.enums.dq;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqCheckRunStatus implements EnumClass<String> {

    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String id;

    DqCheckRunStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqCheckRunStatus fromId(String id) {
        for (DqCheckRunStatus at : DqCheckRunStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}