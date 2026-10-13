package uz.kapitalbank.umida.service.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleQueries;
import uz.kapitalbank.umida.dto.dq.DqSqlQuery;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;
import uz.kapitalbank.umida.utils.DateUtils;
import uz.kapitalbank.umida.utils.JsonUtils;
import uz.kapitalbank.umida.utils.NumberUtils;
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
import java.util.stream.Collectors;

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
 *     <li>a <b>violations</b> query returning every violating row, projected onto
 *     {@code ruleConfig.samplesQueryColumns} ({@value #ALL_COLUMNS}, or an absent list, keeps the
 *     whole row). It carries no row limit: whoever runs it pages through it.</li>
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
     * The {@code samplesQueryColumns} entry standing for "every column". A wide table would otherwise
     * pin a column list into the rule config that the next table change would invalidate, so both an
     * empty selection and a complete one are stored as this single value.
     */
    public static final String ALL_COLUMNS = "*";

    /**
     * Accepted shape of a table or column name. Everything else is rejected rather than escaped:
     * these names reach the statement as text, and the rule editor only ever offers names read back
     * from the JDBC metadata.
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,127}");

    private static final String ROW_ALIAS = "t";
    private static final String DUPLICATE_ALIAS = "d";
    private static final String REFERENCE_ALIAS = "r";

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
            case NOT_BLANK -> buildNotBlank(rule, config, dialect);
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
     * Violating row: the column holds no value. Empty strings are values and pass — use a NOT_BLANK
     * rule to reject them too.
     */
    private DqRuleQueries buildNotNull(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String column = columnRef(rule, dialect);
        return rowLevelQueries(rule, config, dialect, new Predicate(column + " IS NULL", List.of()), null);
    }

    /**
     * Violating row: the character column holds no value, an empty string, or nothing but whitespace
     * (spaces, tabs, line breaks) — the completeness check of NOT_NULL for columns where an empty
     * string stands in for a missing value. Meant for character columns: on any other column
     * PostgreSQL rejects the pattern match and the rule is skipped, while MySQL and Oracle convert the
     * value to text and the rule acts as NOT_NULL.
     */
    private DqRuleQueries buildNotBlank(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String column = columnRef(rule, dialect);
        return rowLevelQueries(rule, config, dialect, new Predicate(dialect.blankMatch(column), List.of()), null);
    }

    /**
     * Violating row: the column value occurs more than once in the table. Every member of a duplicate
     * group counts, not just the surplus ones, so the violating rows show the full group. {@code NULL} never
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

        // ordering keeps the members of a duplicate group adjacent in the violating rows
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

    /**
     * Violating row: the column holds a value that does not occur in the reference column
     * ({@code refSchema.refTable.refColumn}, same data source). {@code NULL} is a valid value unless
     * {@code allowNull} is explicitly false.
     * <p>
     * The reference is joined as its DISTINCT values rather than tested with a correlated
     * {@code NOT EXISTS}: inside the CASE of the metrics query a correlated subquery runs once per
     * row, while a join is planned as a hash join. DISTINCT keeps a duplicated reference value from
     * multiplying the checked rows, and joining keeps a NULL in the reference column harmless — the
     * trap of {@code NOT IN}. The reference table may be the checked table itself ({@code parent_id}
     * against {@code id}): the two are told apart by their aliases.
     */
    private DqRuleQueries buildReferential(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        String column = columnRef(rule, dialect);
        String refTable = qualifiedTableRef(config.getRefSchema(), config.getRefTable(), "Reference table",
                rule, dialect);
        String refColumnName = config.getRefColumn();
        if (!StringUtils.hasText(refColumnName)) {
            throw new IllegalArgumentException("Reference column is not set for rule: " + rule.getName());
        }
        String quotedRefColumn = dialect.quote(requireIdentifier(refColumnName.trim(), "Reference column", rule));
        String refColumn = REFERENCE_ALIAS + "." + quotedRefColumn;

        String join = "LEFT JOIN (SELECT DISTINCT " + quotedRefColumn + " FROM " + refTable + ") " + REFERENCE_ALIAS
                + " ON " + refColumn + " = " + column;
        // the join matches on equality, so the reference side is NULL exactly when nothing matched
        String notFound = refColumn + " IS NULL";
        String predicate = Boolean.FALSE.equals(config.getAllowNull())
                ? column + " IS NULL OR " + notFound
                : column + " IS NOT NULL AND " + notFound;

        // ordering keeps the rows sharing an unknown value adjacent in the violating rows
        return rowLevelQueries(rule, config, dialect, join, new Predicate(predicate, List.of()), column);
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
     * Builds the metrics/violations pair from a predicate that identifies a single violating row.
     *
     * @param orderByColumn column expression to order the violating rows by, or {@code null} to leave
     *                      the order to the database
     */
    private DqRuleQueries rowLevelQueries(DqRule rule, DqRuleConfig config, DqSqlDialect dialect,
                                          Predicate violation, @Nullable String orderByColumn) {
        return rowLevelQueries(rule, config, dialect, null, violation, orderByColumn);
    }

    /**
     * @param join join clause appended to the rule's table, or {@code null} for none. It must match
     *             at most one row per row of the rule's table, or the total count would grow.
     */
    private DqRuleQueries rowLevelQueries(DqRule rule, DqRuleConfig config, DqSqlDialect dialect,
                                          @Nullable String join, Predicate violation,
                                          @Nullable String orderByColumn) {
        String from = " FROM " + tableRef(rule, dialect) + " " + ROW_ALIAS + (join != null ? " " + join : "");

        // the predicate is parenthesized at both use sites: it may contain OR, and both sites may grow
        String metrics = "SELECT COUNT(*) AS " + COLUMN_TOTAL_COUNT
                + ", COUNT(CASE WHEN (" + violation.sql() + ") THEN 1 END) AS " + COLUMN_FAILED_COUNT
                + from;

        StringBuilder violations = new StringBuilder("SELECT ").append(violationsProjection(rule, config, dialect))
                .append(from)
                .append(" WHERE (").append(violation.sql()).append(')');
        if (orderByColumn != null) {
            violations.append(" ORDER BY ").append(orderByColumn);
        }

        return new DqRuleQueries(
                new DqSqlQuery(metrics, violation.params()),
                new DqSqlQuery(violations.toString(), violation.params()));
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

    /**
     * The select list of the violations query. Selecting only the columns worth looking at keeps the
     * violating rows readable on tables that carry hundreds of them; the whole row is selected when
     * the configuration names {@value #ALL_COLUMNS} or names nothing usable.
     */
    private String violationsProjection(DqRule rule, DqRuleConfig config, DqSqlDialect dialect) {
        List<String> columns = config.getSamplesQueryColumns();
        if (columns == null || columns.contains(ALL_COLUMNS)) {
            return ROW_ALIAS + ".*";
        }

        String projection = columns.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(column -> ROW_ALIAS + "." + dialect.quote(requireIdentifier(column, "Sample column", rule)))
                .collect(Collectors.joining(", "));
        return projection.isEmpty() ? ROW_ALIAS + ".*" : projection;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * The rule's table, quoted and schema-qualified: from the rule's schema, or from the table name
     * itself when that already contains a dot (rules created before the schema was selectable).
     */
    private String tableRef(DqRule rule, DqSqlDialect dialect) {
        return qualifiedTableRef(rule.getDbSchema(), rule.getTableName(), "Table name", rule, dialect);
    }

    /**
     * A table, quoted and schema-qualified: from the given schema, or from the table name itself when
     * that already contains a dot.
     *
     * @param what how the table is named in an error message
     */
    private String qualifiedTableRef(@Nullable String schema, @Nullable String tableName, String what,
                                     DqRule rule, DqSqlDialect dialect) {
        if (!StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException(what + " is not set for rule: " + rule.getName());
        }
        tableName = tableName.trim();

        if (StringUtils.hasText(schema) && !tableName.contains(".")) {
            tableName = schema.trim() + "." + tableName;
        }

        StringBuilder ref = new StringBuilder();
        for (String part : tableName.split("\\.", -1)) {
            if (!ref.isEmpty()) {
                ref.append('.');
            }
            ref.append(dialect.quote(requireIdentifier(part, what, rule)));
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
