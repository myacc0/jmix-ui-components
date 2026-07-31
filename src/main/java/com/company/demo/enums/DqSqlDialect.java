package com.company.demo.enums;

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
     * Renders the row-limiting clause appended to the end of a {@code SELECT}. The limit is inlined
     * rather than bound, because Oracle does not accept a bind variable in every position of
     * {@code FETCH FIRST}.
     *
     * @param maxRows positive row count
     */
    public String limitClause(int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("Row limit must be positive: " + maxRows);
        }
        return switch (this) {
            case POSTGRESQL, MYSQL -> "LIMIT " + maxRows;
            case ORACLE -> "FETCH FIRST " + maxRows + " ROWS ONLY";
        };
    }
}
