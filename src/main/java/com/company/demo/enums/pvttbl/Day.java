package com.company.demo.enums.pvttbl;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum Day implements EnumClass<String> {

    THURSDAY("thursday"),
    FRIDAY("friday"),
    SATURDAY("saturday"),
    SUNDAY("sunday");

    private final String id;

    Day(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static Day fromId(String id) {
        for (Day at : Day.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}