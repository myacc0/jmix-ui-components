package com.company.demo.utils;

import org.springframework.lang.Nullable;

public class StringUtils {
    private StringUtils() {}

    public static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public static String asText(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
