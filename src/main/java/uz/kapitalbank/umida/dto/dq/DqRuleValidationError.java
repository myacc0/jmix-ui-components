package uz.kapitalbank.umida.dto.dq;

import org.springframework.lang.Nullable;

/**
 * A single problem found while validating a {@link DqRuleConfig} against its rule type.
 *
 * @param field   logical name of the offending attribute ({@code min}, {@code regexp}, …), or
 *                {@code null} when the problem is not attributable to one field. Views may use it
 *                to attach the message to the matching input component.
 * @param message localized, user-readable description of the problem
 */
public record DqRuleValidationError(@Nullable String field, String message) {

    // ----- logical field names, shared by the validator and the views -----
    public static final String FIELD_TABLE_NAME = "tableName";
    public static final String FIELD_COLUMN_NAME = "columnName";
    public static final String FIELD_RULE_CONFIG = "ruleConfig";
    public static final String FIELD_RULE_TYPE = "ruleType";
    public static final String FIELD_THRESHOLD = "threshold";
    public static final String FIELD_MIN = "min";
    public static final String FIELD_MAX = "max";
    public static final String FIELD_REGEXP = "regexp";
    public static final String FIELD_DUE_DAYS = "dueDays";
    public static final String FIELD_REF_SCHEMA = "refSchema";
    public static final String FIELD_REF_TABLE = "refTable";
    public static final String FIELD_REF_COLUMN = "refColumn";
    public static final String FIELD_SQL = "sql";
    public static final String FIELD_VIOLATIONS_SQL = "violationsSql";
    public static final String FIELD_CROSS_PRIMARY_SQL = "crossPrimarySql";
    public static final String FIELD_CROSS_DATA_SOURCE = "crossDataSource";
    public static final String FIELD_CROSS_DATA_SOURCE_SQL = "crossDataSourceSql";
    public static final String FIELD_CROSS_COLUMN_PAIRS = "crossColumnPairs";

    public static DqRuleValidationError of(String message) {
        return new DqRuleValidationError(null, message);
    }
}
