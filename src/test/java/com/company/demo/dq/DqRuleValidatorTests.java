package com.company.demo.dq;

import com.company.demo.dto.DqRuleValidationError;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.service.DqRuleValidator;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.company.demo.dto.DqRuleValidationError.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqRuleValidatorTests {

    @Autowired
    DqRuleValidator validator;

    @Autowired
    DataManager dataManager;

    private DqRule rule(DqRuleType type, String config) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("test rule");
        rule.setDataSource("main");
        rule.setTableName("employee");
        rule.setColumnName("first_name");
        rule.setRuleType(type);
        rule.setRuleConfig(config);
        return rule;
    }

    private List<String> fields(DqRule rule) {
        return validator.validate(rule).stream()
                .map(DqRuleValidationError::field)
                .toList();
    }

    // ----- generic config attributes -----

    @Test
    void ruleTypeIsRequired() {
        assertEquals(List.of(FIELD_RULE_TYPE), fields(rule(null, "{}")));
    }

    @Test
    void blankConfigIsRejected() {
        assertEquals(List.of(FIELD_RULE_CONFIG), fields(rule(DqRuleType.NOT_NULL, "  ")));
    }

    @Test
    void malformedConfigJsonIsRejected() {
        assertEquals(List.of(FIELD_RULE_CONFIG), fields(rule(DqRuleType.NOT_NULL, "{not json")));
    }

    @Test
    void thresholdOutsideZeroToHundredIsRejected() {
        assertEquals(List.of(FIELD_THRESHOLD), fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": 101}")));
        assertEquals(List.of(FIELD_THRESHOLD), fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": -1}")));
        assertTrue(fields(rule(DqRuleType.NOT_NULL, "{\"threshold\": 100}")).isEmpty());
    }

    @Test
    void sampleSizeOutsideOneToHundredIsRejected() {
        assertEquals(List.of(FIELD_SAMPLE_SIZE), fields(rule(DqRuleType.NOT_NULL, "{\"sampleSize\": 0}")));
        assertEquals(List.of(FIELD_SAMPLE_SIZE), fields(rule(DqRuleType.NOT_NULL, "{\"sampleSize\": -1}")));
        assertEquals(List.of(FIELD_SAMPLE_SIZE), fields(rule(DqRuleType.NOT_NULL, "{\"sampleSize\": 101}")));
        assertTrue(fields(rule(DqRuleType.NOT_NULL, "{\"sampleSize\": 1}")).isEmpty());
        assertTrue(fields(rule(DqRuleType.NOT_NULL, "{\"sampleSize\": 100}")).isEmpty());
        // the config attribute stays optional
        assertTrue(fields(rule(DqRuleType.NOT_NULL, "{}")).isEmpty());
    }

    // ----- NOT_NULL / UNIQUENESS -----

    @ParameterizedTest
    @EnumSource(value = DqRuleType.class, names = {"NOT_NULL", "UNIQUENESS"})
    void columnBoundTypesRequireTableAndColumn(DqRuleType type) {
        assertTrue(fields(rule(type, "{}")).isEmpty());

        DqRule rule = rule(type, "{}");
        rule.setTableName(null);
        rule.setColumnName("");
        assertEquals(List.of(FIELD_TABLE_NAME, FIELD_COLUMN_NAME), fields(rule));
    }

    // ----- REGEXP -----

    @Test
    void regexpIsRequiredAndMustCompile() {
        assertEquals(List.of(FIELD_REGEXP), fields(rule(DqRuleType.REGEXP, "{}")));
        assertEquals(List.of(FIELD_REGEXP), fields(rule(DqRuleType.REGEXP, "{\"regexp\": \"[a-\"}")));
        assertTrue(fields(rule(DqRuleType.REGEXP, "{\"regexp\": \"^[A-Z][a-z]+$\"}")).isEmpty());
    }

    // ----- RANGE_NUMBER -----

    @Test
    void numberRangeNeedsAtLeastOneBound() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_NUMBER, "{}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 1}")).isEmpty());
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"max\": 1}")).isEmpty());
    }

    @Test
    void numberRangeBoundsMustBeNumeric() {
        assertEquals(List.of(FIELD_MIN, FIELD_MAX),
                fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": \"abc\", \"max\": \"xyz\"}")));
    }

    @Test
    void reversedNumberRangeIsRejected() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 10, \"max\": 5}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 5, \"max\": 10}")).isEmpty());
    }

    @Test
    void equalNumberBoundsMustBothBeIncluded() {
        assertEquals(List.of(FIELD_MIN),
                fields(rule(DqRuleType.RANGE_NUMBER, "{\"min\": 5, \"max\": 5, \"minIncluded\": true}")));
        assertTrue(fields(rule(DqRuleType.RANGE_NUMBER,
                "{\"min\": 5, \"max\": 5, \"minIncluded\": true, \"maxIncluded\": true}")).isEmpty());
    }

    // ----- RANGE_DATE -----

    @Test
    void dateRangeNeedsAtLeastOneBound() {
        assertEquals(List.of(FIELD_MIN), fields(rule(DqRuleType.RANGE_DATE, "{}")));
        assertTrue(fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-01-01\"}")).isEmpty());
    }

    @Test
    void dateRangeBoundsMustBeIsoDates() {
        assertEquals(List.of(FIELD_MAX),
                fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-01-01\", \"max\": \"31.12.2026\"}")));
    }

    @Test
    void reversedDateRangeIsRejected() {
        assertEquals(List.of(FIELD_MIN),
                fields(rule(DqRuleType.RANGE_DATE, "{\"min\": \"2026-12-31\", \"max\": \"2026-01-01\"}")));
        assertTrue(fields(rule(DqRuleType.RANGE_DATE,
                "{\"min\": \"2026-01-01\", \"max\": \"2026-12-31\"}")).isEmpty());
    }

    // ----- not implemented yet: accept anything -----

    @ParameterizedTest
    @EnumSource(value = DqRuleType.class, names = {"REFERENTIAL", "CUSTOM_SQL", "CROSS_SOURCE_AGGREGATED"})
    void placeholderTypesReportNoErrors(DqRuleType type) {
        assertTrue(fields(rule(type, "{}")).isEmpty());
    }
}
