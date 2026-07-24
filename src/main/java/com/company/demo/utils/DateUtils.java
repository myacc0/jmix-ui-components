package com.company.demo.utils;

import java.time.LocalDate;

public class DateUtils {
    private DateUtils() {}

    public static LocalDate toDate(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            try {
                return LocalDate.parse(s);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
