package uz.kapitalbank.umida.enums.dq;

import org.springframework.lang.Nullable;

/**
 * SQL dialect of a data quality data source. Holds every construct that differs between the
 * supported databases, so that {@code DqSqlQueryBuilder} can stay dialect-agnostic.
 * <p>
 * Not a persistent enum: it is derived at runtime from {@code DatabaseMetaData.getDatabaseProductName()}
 * by {@code DqDataSourceProvider.resolveDialect(String)} and never stored in a column.
 */
public enum DqSqlDialect {

    POSTGRESQL,
    MYSQL,
    ORACLE;

    /** A value made of whitespace only, or of nothing at all. */
    private static final String BLANK_PATTERN = "^[[:space:]]*$";

    /**
     * Maps a JDBC {@code DatabaseMetaData.getDatabaseProductName()} value onto a dialect.
     *
     * @return the matching dialect, or {@code null} when the product is not supported
     */
    @Nullable
    public static DqSqlDialect fromProductName(@Nullable String productName) {
        if (productName == null) {
            return null;
        }
        String name = productName.toLowerCase();
        if (name.contains("postgres")) {
            return POSTGRESQL;
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MYSQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        return null;
    }

    /**
     * Wraps an identifier in the dialect's quoting characters. Quoting keeps the identifier exactly
     * as it was read from the JDBC metadata, which is what the rule editor offers for selection.
     */
    public String quote(String identifier) {
        return switch (this) {
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            case POSTGRESQL, ORACLE -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }

    /**
     * Renders a "value matches the regular expression" predicate with the pattern as a bind parameter.
     * <p>
     * Note that the regexp flavours are not identical: PostgreSQL and Oracle use POSIX regular
     * expressions, MySQL 8 uses ICU. Matching is case-sensitive in PostgreSQL and Oracle, and follows
     * the column collation in MySQL.
     *
     * @param columnExpression already-quoted column reference, e.g. {@code t."email"}
     */
    public String regexpMatch(String columnExpression) {
        return switch (this) {
            case POSTGRESQL -> columnExpression + " ~ ?";
            case MYSQL -> columnExpression + " REGEXP ?";
            case ORACLE -> "REGEXP_LIKE(" + columnExpression + ", ?)";
        };
    }

    /**
     * Renders a "value is missing or holds nothing but whitespace" predicate for a character column.
     * <p>
     * Whitespace is the POSIX {@code [[:space:]]} class, understood by all three databases: space, tab,
     * line feed, carriage return, vertical tab and form feed. Whether it also covers the no-break
     * space (U+00A0) is up to the database and its character set.
     * <p>
     * The empty string matches the pattern, except in Oracle, which stores it as {@code NULL} — and a
     * pattern test of {@code NULL} is never true, so {@code IS NULL} is tested on its own everywhere.
     * The pattern is a constant, so it is inlined rather than bound.
     *
     * @param columnExpression already-quoted column reference, e.g. {@code t."email"}
     */
    public String blankMatch(String columnExpression) {
        String blank = "'" + BLANK_PATTERN + "'";
        return columnExpression + " IS NULL OR " + switch (this) {
            case POSTGRESQL -> columnExpression + " ~ " + blank;
            case MYSQL -> columnExpression + " REGEXP " + blank;
            case ORACLE -> "REGEXP_LIKE(" + columnExpression + ", " + blank + ")";
        };
    }

    /**
     * Renders the paging clause appended to the end of a {@code SELECT}. The numbers are inlined
     * rather than bound, because Oracle does not accept a bind variable in every position of
     * {@code OFFSET ... FETCH}.
     *
     * @param firstResult zero-based index of the first row
     * @param maxResults  positive row count
     */
    public String pageClause(int firstResult, int maxResults) {
        if (firstResult < 0) {
            throw new IllegalArgumentException("First row must not be negative: " + firstResult);
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("Row limit must be positive: " + maxResults);
        }
        return switch (this) {
            case POSTGRESQL, MYSQL -> "LIMIT " + maxResults + " OFFSET " + firstResult;
            case ORACLE -> "OFFSET " + firstResult + " ROWS FETCH NEXT " + maxResults + " ROWS ONLY";
        };
    }

    /**
     * Renders a string literal. MySQL, unlike the others, treats a backslash inside a literal as an
     * escape character by default, so there it is doubled to keep a regexp such as {@code \d} intact.
     */
    public String stringLiteral(String value) {
        String escaped = value.replace("'", "''");
        if (this == MYSQL) {
            escaped = escaped.replace("\\", "\\\\");
        }
        return "'" + escaped + "'";
    }
}
