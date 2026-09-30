package com.company.demo.enums.orgstructure;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum OrgStructureEmployeeGender implements EnumClass<String> {

    MALE("male"),
    FEMALE("female");

    private final String id;

    OrgStructureEmployeeGender(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrgStructureEmployeeGender fromId(String id) {
        for (OrgStructureEmployeeGender at : OrgStructureEmployeeGender.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}