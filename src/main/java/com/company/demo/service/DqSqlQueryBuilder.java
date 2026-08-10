package com.company.demo.service;

import com.company.demo.dto.DqRuleConfig;
import com.company.demo.dto.DqRuleQueries;
import com.company.demo.dto.DqSqlQuery;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSqlDialect;
import com.company.demo.utils.DateUtils;
import com.company.demo.utils.JsonUtils;
import com.company.demo.utils.NumberUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Translates a {@link DqRule} into the SQL that measures it, in the dialect of the rule's data source.
 * Consumed by {@code DqCheckExecutor}, which runs the statements and turns the results into
 * {@code DqCheckRunResult} rows.
 * <p>
 * Each rule yields a {@link DqRuleQueries} pair built around one <em>violation predicate</em> — the
 * condition that makes a single row bad for that rule type:
 * <ul>
 *     <li>a <b>metrics</b> query returning {@value #COLUMN_TOTAL_COUNT} and
 *     {@value #COLUMN_FAILED_COUNT} in a single row;</li>
 *     <li>a <b>samples</b> query returning the violating rows, capped by {@code ruleConfig.sampleSize}
 *     (no cap when it is not set).</li>
 * </ul>
 * The {@code threshold} attribute is deliberately not part of the SQL: it compares against the pass
 * rate derived from the two counts and belongs to the executor.
 * <p>
 * Values coming from {@code ruleConfig} are always bound as parameters. Table and column names cannot
 * be bound, so they are validated against {@link #IDENTIFIER_PATTERN} and quoted before they reach
 * the statement.
 *
 * @see DqRuleValidator the counterpart that rejects configurations this builder could not translate
 */
@Service
public class DqSqlQueryBuilder {

    /** Alias of the total row count in the metrics query. */
    public static final String COLUMN_TOTAL_COUNT = "total_count";
    /** Alias of the violating row count in the metrics query. */
    public static final String COLUMN_FAILED_COUNT = "failed_count";

    /**
     * Accepted shape of a table or column name. Everything else is rejected rather than escaped:
     * these names reach the statement as text, and the rule editor only ever offers names read back
     * from the JDBC metadata.
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,127}");

    private static final String ROW_ALIAS = "t";
    private static final String DUPLICATE_ALIAS = "d";

    private final DqDataSourceProvider dataSourceProvider;
    private final ObjectMapper objectMapper;

    public DqSqlQueryBuilder(DqDataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Builds the queries for a rule, detecting the dialect from the rule's data source.
     *
     * @throws IllegalArgumentException      when the rule or its configuration cannot be translated
     * @throws UnsupportedOperationException when the rule type is not implemented yet
     */
    public DqRuleQueries buildQueries(DqRule rule) {
        return buildQueries(rule, dataSourceProvider.resolveDialect(rule.getDataSource()));
    }

    /**
     * Builds the queries for a rule in an explicitly given dialect. Touches no data source, so the
     * translation of every rule type can be asserted without a database.
     *
     * @throws IllegalArgumentException      when the rule or its configuration cannot be translated
     * @throws UnsupportedOperationException when the rule type is not implemented yet
     */
    public DqRuleQueries buildQueries(DqRule rule, DqSqlDialect dialect) {
        DqRuleType ruleType = rule.getRuleType();
        if (ruleType == null) {
            throw new IllegalArgumentException("Rule type is not set for rule: " + rule.getName());
        }

        DqRuleConfig config = JsonUtils.parseConfig(rule.getRuleConfig(), DqRuleConfig.class, objectMapper);
        if (config == null) {
            throw new IllegalArgumentException("Rule configuration is missing or is not valid JSON for rule: "
                    + rule.getName());
        }

        return switch (ruleType) {
            case NOT_NULL -> buildNotNull(rule, config, dialect);
            case UNIQUENESS -> buildUniqueness(rule, config, dialect);
            case REGEXP -> buildRegexp(rule, config, dialect);
            case RANGE_NUMBER -> buildRangeNumber(rule, config, dialect);
            case RANGE_DATE -> buildRangeDate(rule, config, dialect);
            case REFERENTIAL -> buildReferential(rule, config, dialect);
            case CUSTOM_SQL -> buildCustomSql(rule, config, dialect);
            case CROSS_SOURCE_AGGREGATED -> buildCrossSourceAggregated(rule, config, dialect);
        };
    }

    // ---------------------------------------------------------------------
    // per rule type
    // ---------------------------------------------------------------------

    /**
     * Violating row: the column holds no value. Empty strings are values and pass.
     */
    private DqRuleQueries buildNotNull(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String column = columnRef(rule, dialect);
        return rowLevelQueries(rule, config, dialect, new Predicate(column + " IS NULL", List.of()), null);
    }

    /**
     * Violating row: the column value occurs more than once in the table. Every member of a duplicate
     * group counts, not just the surplus ones, so the samples show the full group. {@code NULL} never
     * violates uniqueness, which matches how SQL unique constraints treat it.
     */
    private DqRuleQueries buildUniqueness(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String table = tableRef(rule, dialect);
        String column = columnRef(rule, dialect);
        String duplicateColumn = DUPLICATE_ALIAS + "." + dialect.quote(requireColumnName(rule));

        String predicate = column + " IN (SELECT " + duplicateColumn
                + " FROM " + table + " " + DUPLICATE_ALIAS
                + " GROUP BY " + duplicateColumn
                + " HAVING COUNT(*) > 1)";

        // ordering keeps the members of a duplicate group adjacent in the sample rows
        return rowLevelQueries(rule, config, dialect, new Predicate(predicate, List.of()), column);
    }

    /**
     * Violating row: the column holds a value that the pattern does not match. {@code NULL} is not a
     * pattern violation — use a NOT_NULL rule to require presence.
     */
    private DqRuleQueries buildRegexp(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String column = columnRef(rule, dialect);
        String regexp = config.getRegexp();
        if (!StringUtils.hasText(regexp)) {
            throw new IllegalArgumentException("Regexp is not set for rule: " + rule.getName());
        }

        String predicate = column + " IS NOT NULL AND NOT (" + dialect.regexpMatch(column) + ")";
        return rowLevelQueries(rule, config, dialect, new Predicate(predicate, List.of(regexp)), null);
    }

    /**
     * Violating row: the numeric column holds a value outside the configured bounds. Either bound may
     * be omitted, giving a one-sided range. {@code NULL} is not a range violation.
     */
    private DqRuleQueries buildRangeNumber(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        Double min = boundValue(rule, config.getMin(), NumberUtils::asNumber, "min", "a number");
        Double max = boundValue(rule, config.getMax(), NumberUtils::asNumber, "max", "a number");
        // bound as BigDecimal so that an exact decimal column is not compared against a binary float
        return buildRange(rule, config, dialect,
                min == null ? null : BigDecimal.valueOf(min),
                max == null ? null : BigDecimal.valueOf(max));
    }

    /**
     * Violating row: the date column holds a value outside the configured bounds, which are ISO-8601
     * date strings in the configuration. {@code NULL} is not a range violation.
     */
    private DqRuleQueries buildRangeDate(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        LocalDate min = boundValue(rule, config.getMin(), DateUtils::asDate, "min", "an ISO-8601 date");
        LocalDate max = boundValue(rule, config.getMax(), DateUtils::asDate, "max", "an ISO-8601 date");
        return buildRange(rule, config, dialect,
                min == null ? null : java.sql.Date.valueOf(min),
                max == null ? null : java.sql.Date.valueOf(max));
    }

    private DqRuleQueries buildReferential(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        // TODO not implemented yet: anti-join of the column against refTable.refColumn
        throw new UnsupportedOperationException("Rule type is not supported yet: " + DqRuleType.REFERENTIAL.getId());
    }

    private DqRuleQueries buildCustomSql(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        // TODO not implemented yet: wrap the user-supplied config.sql as the violating-row query
        throw new UnsupportedOperationException("Rule type is not supported yet: " + DqRuleType.CUSTOM_SQL.getId());
    }

    private DqRuleQueries buildCrossSourceAggregated(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        // TODO not implemented yet: needs a second query against config.secondaryDataSourceId,
        //  compared in the executor rather than by the database
        throw new UnsupportedOperationException("Rule type is not supported yet: "
                + DqRuleType.CROSS_SOURCE_AGGREGATED.getId());
    }

    // ---------------------------------------------------------------------
    // query assembly
    // ---------------------------------------------------------------------

    /**
     * Builds the metrics/samples pair from a predicate that identifies a single violating row.
     *
     * @param orderByColumn column expression to order the sample rows by, or {@code null} to leave
     *                      the order to the database
     */
    private DqRuleQueries rowLevelQueries(DqRule rule, DqRuleConfig config, DqSqlDialect dialect,
                                          Predicate violation, @Nullable String orderByColumn) {
        String from = " FROM " + tableRef(rule, dialect) + " " + ROW_ALIAS;

        // the predicate is parenthesized at both use sites: it may contain OR, and both sites may grow
        String metrics = "SELECT COUNT(*) AS " + COLUMN_TOTAL_COUNT
                + ", COUNT(CASE WHEN (" + violation.sql() + ") THEN 1 END) AS " + COLUMN_FAILED_COUNT
                + from;

        StringBuilder samples = new StringBuilder("SELECT ").append(ROW_ALIAS).append(".*")
                .append(from)
                .append(" WHERE (").append(violation.sql()).append(')');
        if (orderByColumn != null) {
            samples.append(" ORDER BY ").append(orderByColumn);
        }
        Integer sampleSize = config.getSampleSize();
        if (sampleSize != null && sampleSize > 0) {
            samples.append(' ').append(dialect.limitClause(sampleSize));
        }

        return new DqRuleQueries(
                new DqSqlQuery(metrics, violation.params()),
                new DqSqlQuery(samples.toString(), violation.params()));
    }

    /**
     * Shared assembly for both range types: the bounds differ only in the Java type of the bind value.
     * A bound is inclusive unless the configuration explicitly says otherwise.
     */
    private DqRuleQueries buildRange(DqRule rule, DqRuleConfig config, DqSqlDialect dialect,
                                     @Nullable Object min, @Nullable Object max) {
        if (min == null && max == null) {
            throw new IllegalArgumentException("Neither min nor max is set for rule: " + rule.getName());
        }

        String column = columnRef(rule, dialect);
        List<String> outOfRange = new ArrayList<>(2);
        List<Object> params = new ArrayList<>(2);

        // What is assembled here is the VIOLATION condition, so each bound is negated: an inclusive
        // min means the allowed values are "column >= min", which makes a row bad when it is
        // strictly below the bound. Using <= for an inclusive bound would flag column == min, i.e.
        // the one value the bound explicitly admits.
        //
        //   minIncluded=true   allowed: column >= ?   violating: column <  ?
        //   minIncluded=false  allowed: column >  ?   violating: column <= ?
        //   maxIncluded=true   allowed: column <= ?   violating: column >  ?
        //   maxIncluded=false  allowed: column <  ?   violating: column >= ?
        if (min != null) {
            outOfRange.add(column + (isIncluded(config.getMinIncluded()) ? " < ?" : " <= ?"));
            params.add(min);
        }
        if (max != null) {
            outOfRange.add(column + (isIncluded(config.getMaxIncluded()) ? " > ?" : " >= ?"));
            params.add(max);
        }

        String predicate = column + " IS NOT NULL AND (" + String.join(" OR ", outOfRange) + ")";
        return rowLevelQueries(rule, config, dialect, new Predicate(predicate, params), null);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * The rule's table, quoted and optionally schema-qualified when the name contains a dot.
     */
    private String tableRef(DqRule rule, DqSqlDialect dialect) {
        String tableName = rule.getTableName();
        if (!StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException("Table name is not set for rule: " + rule.getName());
        }

        StringBuilder ref = new StringBuilder();
        for (String part : tableName.trim().split("\\.", -1)) {
            if (!ref.isEmpty()) {
                ref.append('.');
            }
            ref.append(dialect.quote(requireIdentifier(part, "Table name", rule)));
        }
        return ref.toString();
    }

    /**
     * The rule's column, quoted and qualified with the row alias.
     */
    private String columnRef(DqRule rule, DqSqlDialect dialect) {
        return ROW_ALIAS + "." + dialect.quote(requireColumnName(rule));
    }

    private String requireColumnName(DqRule rule) {
        String columnName = rule.getColumnName();
        if (!StringUtils.hasText(columnName)) {
            throw new IllegalArgumentException("Column name is not set for rule: " + rule.getName());
        }
        return requireIdentifier(columnName.trim(), "Column name", rule);
    }

    private String requireIdentifier(String identifier, String what, DqRule rule) {
        if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(what + " '" + identifier + "' is not a valid SQL identifier, rule: "
                    + rule.getName());
        }
        return identifier;
    }

    /**
     * Converts a raw configuration bound to its typed value, rejecting a bound that is present but
     * unusable rather than silently dropping it — dropping it would widen the range being checked.
     */
    @Nullable
    private <T> T boundValue(DqRule rule, @Nullable Object raw, Function<Object, T> converter,
                             String boundName, String expected) {
        if (raw == null) {
            return null;
        }
        T value = converter.apply(raw);
        if (value == null) {
            throw new IllegalArgumentException("Bound '" + boundName + "' is not " + expected + ": " + raw
                    + ", rule: " + rule.getName());
        }
        return value;
    }

    private boolean isIncluded(@Nullable Boolean included) {
        return !Boolean.FALSE.equals(included);
    }

    /**
     * A SQL condition together with the bind values its placeholders consume.
     */
    private record Predicate(String sql, List<Object> params) {
    }
}
