package com.company.demo.utils;

public class NumberUtils {
    private NumberUtils() {}

    public static Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
