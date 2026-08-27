package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum WorkDisciplineTargetType implements EnumClass<String> {

    SUBDIVISION("SUBDIVISION"),
    JOB_TITLE("JOB_TITLE"),
    EMPLOYEE("EMPLOYEE");

    private final String id;

    WorkDisciplineTargetType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public static WorkDisciplineTargetType fromId(String id) {
        for (WorkDisciplineTargetType at : WorkDisciplineTargetType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
