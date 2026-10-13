package uz.kapitalbank.umida.service.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.utils.DateUtils;
import uz.kapitalbank.umida.utils.NumberUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static uz.kapitalbank.umida.dto.dq.DqRuleValidationError.*;

/**
 * Validates the {@code ruleConfig} JSON of a {@link DqRule} against the requirements of its
 * {@link DqRuleType}. Called before a rule is saved (on create and on update).
 * <p>
 * Contains no UI dependencies: it returns localized problem descriptions and lets the caller
 * decide how to present them.
 */
@Service
public class DqRuleValidator {
    private static final Logger log = LoggerFactory.getLogger(DqRuleValidator.class);

    private static final int MIN_THRESHOLD = 0;
    private static final int MAX_THRESHOLD = 100;

    /** The accepted issue resolution period, in days, also used by the editor to bound its input. */
    public static final int MIN_DUE_DAYS = 1;
    public static final int MAX_DUE_DAYS = 365;

    /** Describing a statement does not read its rows, so it is expected to answer quickly. */
    private static final int CUSTOM_SQL_DESCRIBE_TIMEOUT_SECONDS = 30;

    private final Messages messages;
    private final DqDataSourceProvider dataSourceProvider;
    private final ObjectMapper objectMapper;

    public DqRuleValidator(Messages messages, DqDataSourceProvider dataSourceProvider) {
        this.messages = messages;
        this.dataSourceProvider = dataSourceProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates the rule configuration.
     *
     * @return the problems found, empty when the configuration is valid
     */
    public List<DqRuleValidationError> validate(DqRule rule) {
        List<DqRuleValidationError> errors = new ArrayList<>();

        Integer dueDays = rule.getDueDays();
        if (dueDays != null && (dueDays < MIN_DUE_DAYS || dueDays > MAX_DUE_DAYS)) {
            errors.add(formatError(FIELD_DUE_DAYS, "dqRuleValidation.dueDaysOutOfRange",
                    MIN_DUE_DAYS, MAX_DUE_DAYS));
        }

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
            case NOT_BLANK -> validateNotBlank(rule, config, errors);
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
    }

    // ---------------------------------------------------------------------
    // per rule type
    // ---------------------------------------------------------------------

    private void validateNotNull(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        requireTable(rule, errors);
        requireColumn(rule, errors);
    }

    private void validateNotBlank(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
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
        requireTable(rule, errors);
        requireColumn(rule, errors);

        boolean complete = true;
        if (!StringUtils.hasText(config.getRefSchema())) {
            errors.add(error(FIELD_REF_SCHEMA, "dqRuleValidation.refSchemaRequired"));
            complete = false;
        }
        if (!StringUtils.hasText(config.getRefTable())) {
            errors.add(error(FIELD_REF_TABLE, "dqRuleValidation.refTableRequired"));
            complete = false;
        }
        if (!StringUtils.hasText(config.getRefColumn())) {
            errors.add(error(FIELD_REF_COLUMN, "dqRuleValidation.refColumnRequired"));
            complete = false;
        }
        if (complete && StringUtils.hasText(rule.getTableName()) && StringUtils.hasText(rule.getColumnName())) {
            validateReferenceTypes(rule, config, errors);
        }
    }

    /**
     * Both statements are the user's own and must be single read queries: the metrics query returns
     * the total and the failed counts, the violations query the failed records themselves.
     */
    private void validateCustomSql(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        boolean sqlReadable = validateReadQuery(config.getSql(), FIELD_SQL, "dqRuleValidation.sqlRequired", errors);
        boolean violationsSqlReadable = validateReadQuery(config.getViolationsSql(), FIELD_VIOLATIONS_SQL,
                "dqRuleValidation.violationsSqlRequired", errors);
        if (!StringUtils.hasText(rule.getDataSource())) {
            return;
        }
        if (sqlReadable) {
            describeCustomSql(rule, DqCustomSql.normalize(config.getSql()), FIELD_SQL, errors)
                    .ifPresent(columns -> validateMetricsColumns(columns, errors));
        }
        if (violationsSqlReadable) {
            // any columns will do: they are shown as they are
            describeCustomSql(rule, DqCustomSql.normalize(config.getViolationsSql()), FIELD_VIOLATIONS_SQL, errors);
        }
    }

    /** Reports a statement that is not a single read query; returns whether it is one. */
    private boolean validateReadQuery(@Nullable String sql, String field, String requiredKey,
                                      List<DqRuleValidationError> errors) {
        DqCustomSql.Violation violation = DqCustomSql.check(sql);
        if (violation == null) {
            return true;
        }
        errors.add(switch (violation.problem()) {
            case BLANK -> error(field, requiredKey);
            case MULTIPLE_STATEMENTS -> error(field, "dqRuleValidation.sqlMultipleStatements");
            case NOT_SELECT -> error(field, "dqRuleValidation.sqlNotSelect");
            case MODIFYING_KEYWORD -> formatError(field, "dqRuleValidation.sqlModifiesData", violation.word());
        });
        return false;
    }

    /**
     * Asks the data source what a statement returns. A statement the database rejects is reported
     * with the database's own message; when the data source cannot be reached, the statement is left
     * to the run and nothing is returned.
     */
    private Optional<List<DqDataSourceProvider.ColumnType>> describeCustomSql(DqRule rule, String sql, String field,
                                                                              List<DqRuleValidationError> errors) {
        try {
            return Optional.of(dataSourceProvider.describeQuery(rule.getDataSource(), sql,
                    CUSTOM_SQL_DESCRIBE_TIMEOUT_SECONDS));
        } catch (DqDataSourceProvider.QueryFailedException e) {
            errors.add(formatError(field, "dqRuleValidation.sqlFailed", e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("A query of rule '{}' could not be described, its columns are not checked", rule.getName(), e);
        }
        return Optional.empty();
    }

    /** The metrics query returns two numeric columns, the total count first. */
    private void validateMetricsColumns(List<DqDataSourceProvider.ColumnType> columns,
                                        List<DqRuleValidationError> errors) {
        if (columns.size() != 2) {
            errors.add(formatError(FIELD_SQL, "dqRuleValidation.sqlColumnCount", columns.size()));
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            DqDataSourceProvider.ColumnType column = columns.get(i);
            if (typeFamily(column.jdbcType()) != TypeFamily.NUMBER) {
                errors.add(formatError(FIELD_SQL, "dqRuleValidation.sqlColumnNotNumber", i + 1, column.typeName()));
            }
        }
    }

    private void validateCrossSourceAggregated(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        // TODO not implemented yet
    }

    /**
     * Rejects a reference column the checked column cannot be compared with: the database would
     * only fail on it when the check runs ({@code operator does not exist: character varying =
     * integer}). The types are read from the data source; when either cannot be read — the data
     * source is unreachable, or a column is gone — the comparison is left to the run.
     */
    private void validateReferenceTypes(DqRule rule, DqRuleConfig config, List<DqRuleValidationError> errors) {
        DqDataSourceProvider.ColumnType columnType;
        DqDataSourceProvider.ColumnType refColumnType;
        try {
            columnType = dataSourceProvider.getColumnType(rule.getDataSource(), rule.getDbSchema(),
                    rule.getTableName().trim(), rule.getColumnName().trim());
            refColumnType = dataSourceProvider.getColumnType(rule.getDataSource(), config.getRefSchema().trim(),
                    config.getRefTable().trim(), config.getRefColumn().trim());
        } catch (RuntimeException e) {
            log.warn("Column types of rule '{}' could not be read, the reference column type is not checked",
                    rule.getName(), e);
            return;
        }
        if (columnType == null || refColumnType == null) {
            return;
        }
        if (!areComparable(columnType, refColumnType)) {
            errors.add(formatError(FIELD_REF_COLUMN, "dqRuleValidation.refColumnTypeMismatch",
                    refColumnType.typeName(), columnType.typeName()));
        }
    }

    /**
     * Columns of one type family compare without a cast: any two strings, any two numbers, a date
     * with a timestamp. Types outside the families (uuid, json, …) must be the same type.
     */
    private boolean areComparable(DqDataSourceProvider.ColumnType a, DqDataSourceProvider.ColumnType b) {
        TypeFamily family = typeFamily(a.jdbcType());
        if (family != null) {
            return family == typeFamily(b.jdbcType());
        }
        return typeFamily(b.jdbcType()) == null
                && a.typeName() != null
                && a.typeName().toLowerCase(Locale.ROOT).equals(
                        b.typeName() == null ? null : b.typeName().toLowerCase(Locale.ROOT));
    }

    @Nullable
    private TypeFamily typeFamily(int jdbcType) {
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                 Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> TypeFamily.STRING;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.DECIMAL, Types.NUMERIC, Types.REAL, Types.FLOAT, Types.DOUBLE -> TypeFamily.NUMBER;
            case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> TypeFamily.DATE_TIME;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> TypeFamily.TIME;
            case Types.BIT, Types.BOOLEAN -> TypeFamily.BOOLEAN;
            default -> null;
        };
    }

    private enum TypeFamily {
        STRING, NUMBER, DATE_TIME, TIME, BOOLEAN
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
