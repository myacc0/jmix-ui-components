package com.company.demo.enums.pvttbl;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum Time implements EnumClass<String> {

    DINNER("dinner"),
    LUNCH("lunch");

    private final String id;

    Time(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static Time fromId(String id) {
        for (Time at : Time.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}