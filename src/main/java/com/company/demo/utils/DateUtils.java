package com.company.demo.utils;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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

    public static LocalDate asDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }
}
