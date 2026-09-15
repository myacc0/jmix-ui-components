package com.company.demo.enums.tablesync;

import org.springframework.lang.Nullable;

public enum TableColJavaType {
    UUID("java.util.UUID"),
    STRING("java.lang.String"),
    BOOLEAN("java.lang.Boolean"),
    SHORT("java.lang.Short"),
    INTEGER("java.lang.Integer"),
    LONG("java.lang.Long"),
    DOUBLE("java.lang.Double"),
    BIGDECIMAL("java.lang.BigDecimal"),
    LOCALDATE("java.lang.LocalDate"),
    LOCALDATETIME("java.lang.LocalDateTime"),
    OFFSETDATETIME("java.lang.OffsetDateTime"),
    BYTEARRAY("byte[]");

    private final String id;

    TableColJavaType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static TableColJavaType fromId(String id) {
        for (TableColJavaType t : TableColJavaType.values()) {
            if (t.getId().equals(id)) {
                return t;
            }
        }
        return null;
    }
}
