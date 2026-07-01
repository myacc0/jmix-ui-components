package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DqSeverity implements EnumClass<String> {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String id;

    DqSeverity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqSeverity fromId(String id) {
        for (DqSeverity at : DqSeverity.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}