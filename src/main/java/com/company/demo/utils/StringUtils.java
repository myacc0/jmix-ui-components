package com.company.demo.utils;

public class StringUtils {
    private StringUtils() {}

    public static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
