package com.company.demo.utils;

import org.springframework.util.StringUtils;

public class NumberUtils {
    private NumberUtils() {}

    public static Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    public static Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return Double.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
