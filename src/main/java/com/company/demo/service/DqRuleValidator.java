package com.company.demo.service;

import com.company.demo.dto.DqRuleConfig;
import com.company.demo.dto.DqRuleValidationError;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.utils.DateUtils;
import com.company.demo.utils.NumberUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.Messages;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.company.demo.dto.DqRuleValidationError.*;

/**
 * Validates the {@code ruleConfig} JSON of a {@link DqRule} against the requirements of its
 * {@link DqRuleType}. Called before a rule is saved (on create and on update).
 * <p>
 * Contains no UI dependencies: it returns localized problem descriptions and lets the caller
 * decide how to present them.
 */
@Service
public class DqRuleValidator {

    private static final int MIN_THRESHOLD = 0;
    private static final int MAX_THRESHOLD = 100;

    /** The accepted sample size, also used by the editor to bound its input. */
    public static final int MIN_SAMPLE_SIZE = 1;
    public static final int MAX_SAMPLE_SIZE = 100;
    public static final int DEFAULT_SAMPLE_SIZE = 10;

    private final Messages messages;
    private final ObjectMapper objectMapper;

    public DqRuleValidator(Messages messages) {
        this.messages = messages;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates the rule configuration.
     *
     * @return the problems found, empty when the configuration is valid
     */
    public List<DqRuleValidationError> validate(DqRule rule) {
        List<DqRuleValidationError> errors = new ArrayList<>();

        DqRuleType type = rule.getRuleType();
        if (type == null) {
            errors.add(error(FIELD_RULE_TYPE, "dqRuleValidation.ruleTypeRequired"));
            return errors;
        }

        String json = rule.getRuleConfig();
        if (!StringUtils.hasText(json)) {
            errors.add(error(FIELD_RULE_CONFIG, "dqRuleValidation.configRequired"));
            return errors;
        }

        DqRuleConfig config;
        try {
            config = objectMapper.readValue(json, DqRuleConfig.class);
        } catch (Exception e) {
            errors.add(error(FIELD_RULE_CONFIG, "dqRuleValidation.configInvalidJson"));
            return errors;
        }
        if (config == null) {
            errors.add(error(FIELD_RULE_CONFIG, "dqRuleValidation.configRequired"));
            return errors;
        }

        validateCommon(config, errors);

        switch (type) {
            case NOT_NULL -> validateNotNull(rule, config, errors);
            case UNIQUENESS -> validateUniqueness(rule, config, errors);
            case REGEXP -> validateRegexp(rule, config, errors);
            case RANGE_NUMBER -> validateRangeNumber(rule, config, errors);
            case RANGE_DATE -> validateRangeDate(rule, config, errors);
            case REFERENTIAL -> validateReferential(rule, config, errors);
            case CUSTOM_SQL -> validateCustomSql(rule, config, errors);
            case CROSS_SOURCE_AGGREGATED -> validateCrossSourceAggregated(rule, config, errors);
        }

        return errors;
    }

    // ---------------------------------------------------------------------
    // common config attributes, present for every rule type
    // ---------------------------------------------------------------------

    private void validateCommon(DqRuleConfig config, List<DqRuleValidationError> errors) {
        Double threshold = config.getThreshold();
        if (threshold != null && (threshold < MIN_THRESHOLD || threshold > MAX_THRESHOLD)) {
            errors.add(formatError(FIELD_THRESHOLD, "dqRuleValidation.thresholdOutOfRange",
                    MIN_THRESHOLD, MAX_THRESHOLD));
        }

        Integer sampleSize = config.getSampleSize();
        if (sampleSize != null && (sampleSize < MIN_SAMPLE_SIZE || sampleSize > MAX_SAMPLE_SIZE)) {
            errors.add(formatError(FIELD_SAMPLE_SIZE, "dqRuleValidation.sampleSizeOutOfRange",
                    MIN_SAMPLE_SIZE, MAX_SAMPLE_SIZE));
        }
    }

    // ---------------------------------------------------------------------
    // per rule type
    // ---------------------------------------------------------------------

    private void validateNotNull(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);
    }

    private void validateUniqueness(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);
    }

    private void validateRegexp(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);

        String regexp = config.getRegexp();
        if (!StringUtils.hasText(regexp)) {
            errors.add(error(FIELD_REGEXP, "dqRuleValidation.regexpRequired"));
            return;
        }
        try {
            Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            errors.add(formatError(FIELD_REGEXP, "dqRuleValidation.regexpInvalid", e.getDescription()));
        }
    }

    private void validateRangeNumber(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);

        Object rawMin = config.getMin();
        Object rawMax = config.getMax();
        if (rawMin == null && rawMax == null) {
            errors.add(error(FIELD_MIN, "dqRuleValidation.rangeBoundRequired"));
            return;
        }

        Double min = NumberUtils.asNumber(rawMin);
        if (rawMin != null && min == null) {
            errors.add(formatError(FIELD_MIN, "dqRuleValidation.rangeMinNotNumber", rawMin));
        }
        Double max = NumberUtils.asNumber(rawMax);
        if (rawMax != null && max == null) {
            errors.add(formatError(FIELD_MAX, "dqRuleValidation.rangeMaxNotNumber", rawMax));
        }

        if (min != null && max != null) {
            validateBoundOrder(min.compareTo(max), config, errors);
        }
    }

    private void validateRangeDate(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);

        Object rawMin = config.getMin();
        Object rawMax = config.getMax();
        if (rawMin == null && rawMax == null) {
            errors.add(error(FIELD_MIN, "dqRuleValidation.rangeBoundRequired"));
            return;
        }

        LocalDate min = DateUtils.asDate(rawMin);
        if (rawMin != null && min == null) {
            errors.add(formatError(FIELD_MIN, "dqRuleValidation.rangeMinNotDate", rawMin));
        }
        LocalDate max = DateUtils.asDate(rawMax);
        if (rawMax != null && max == null) {
            errors.add(formatError(FIELD_MAX, "dqRuleValidation.rangeMaxNotDate", rawMax));
        }

        if (min != null && max != null) {
            validateBoundOrder(min.compareTo(max), config, errors);
        }
    }

    private void validateReferential(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        // TODO not implemented yet
    }

    private void validateCustomSql(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        // TODO not implemented yet
    }

    private void validateCrossSourceAggregated(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        // TODO not implemented yet
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void requireTable(DqRule rule, List<DqRuleValidationError> errors) {
        if (!StringUtils.hasText(rule.getTableName())) {
            errors.add(error(FIELD_TABLE_NAME, "dqRuleValidation.tableNameRequired"));
        }
    }

    private void requireColumn(DqRule rule, List<DqRuleValidationError> errors) {
        if (!StringUtils.hasText(rule.getColumnName())) {
            errors.add(error(FIELD_COLUMN_NAME, "dqRuleValidation.columnNameRequired"));
        }
    }

    /**
     * Rejects a reversed range, and an equal pair of bounds that excludes at least one of them —
     * such a range matches nothing.
     */
    private void validateBoundOrder(int comparison, DqRuleConfig config, List<DqRuleValidationError> errors) {
        if (comparison > 0) {
            errors.add(error(FIELD_MIN, "dqRuleValidation.rangeMinGreaterThanMax"));
        } else if (comparison == 0
                && !(Boolean.TRUE.equals(config.getMinIncluded()) && Boolean.TRUE.equals(config.getMaxIncluded()))) {
            errors.add(error(FIELD_MIN, "dqRuleValidation.rangeEmpty"));
        }
    }

    private DqRuleValidationError error(String field, String key) {
        return new DqRuleValidationError(field, messages.getMessage(DqRuleValidator.class, key));
    }

    private DqRuleValidationError formatError(String field, String key, Object... params) {
        return new DqRuleValidationError(field, messages.formatMessage(DqRuleValidator.class, key, params));
    }
}
