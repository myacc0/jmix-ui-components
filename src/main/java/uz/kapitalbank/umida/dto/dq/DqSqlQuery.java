package uz.kapitalbank.umida.dto.dq;

import uz.kapitalbank.umida.enums.dq.DqSqlDialect;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A single executable statement produced by {@code DqSqlQueryBuilder}: parameterized SQL plus the
 * positional bind values for its {@code ?} placeholders, in order.
 * <p>
 * Every value taken from the rule configuration is bound, never inlined, so a hand-edited
 * {@code ruleConfig} cannot inject SQL. Run it with
 * {@code jdbcTemplate.query(query.sql(), rowMapper, query.paramArray())}.
 *
 * @param sql    SQL text with {@code ?} placeholders
 * @param params bind values, positionally matching the placeholders
 */
public record DqSqlQuery(String sql, List<Object> params) {

    public DqSqlQuery {
        params = List.copyOf(params);
    }

    public static DqSqlQuery of(String sql) {
        return new DqSqlQuery(sql, List.of());
    }

    /**
     * The bind values as the varargs array expected by {@code JdbcTemplate}.
     */
    public Object[] paramArray() {
        return params.toArray();
    }

    /**
     * Renders the statement with its bind values substituted in as literals of the given dialect. The
     * result is stored in {@code DqCheckRunResult.executedQuery} for reading and in
     * {@code DqCheckRunResult.violationsQuery} to be run again when the violating rows are viewed.
     * <p>
     * The substitution is a plain left-to-right pass over the {@code ?} characters, which is exact here
     * only because the builder never emits string literals of its own.
     */
    public String toDisplayString(DqSqlDialect dialect) {
        StringBuilder rendered = new StringBuilder(sql.length() + 32);
        int paramIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && paramIndex < params.size()) {
                rendered.append(renderLiteral(params.get(paramIndex++), dialect));
            } else {
                rendered.append(c);
            }
        }
        return rendered.toString();
    }

    private static String renderLiteral(Object value, DqSqlDialect dialect) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof java.sql.Date || value instanceof LocalDate) {
            return "DATE '" + value + "'";
        }
        return dialect.stringLiteral(value.toString());
    }
}
