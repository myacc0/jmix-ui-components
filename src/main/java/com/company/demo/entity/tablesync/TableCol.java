package com.company.demo.entity.tablesync;

public record TableCol(
        String name,
        String javaType,
        int sqlType,
        String foreignTable,
        boolean primaryKey,
        boolean nullable) {
}
