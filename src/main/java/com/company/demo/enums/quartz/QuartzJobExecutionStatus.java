package com.company.demo.enums.quartz;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum QuartzJobExecutionStatus implements EnumClass<String> {

    SUCCESS("success"),
    FAILED("failed");

    private final String id;

    QuartzJobExecutionStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static QuartzJobExecutionStatus fromId(String id) {
        for (QuartzJobExecutionStatus at : QuartzJobExecutionStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
