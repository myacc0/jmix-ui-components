package com.company.demo.enums;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum DqIssueStatus implements EnumClass<String> {

    OPEN("open"),
    IN_PROGRESS("in_progress"),
    RESOLVED("resolved"),
    WONTFIX("wontfix"),
    FALSE_POSITIVE("false_positive");

    private final String id;

    DqIssueStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DqIssueStatus fromId(String id) {
        for (DqIssueStatus at : DqIssueStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}