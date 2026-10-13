package uz.kapitalbank.umida.service.dq;

import org.springframework.lang.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text-level rules for the user-written statement of a {@code CUSTOM_SQL} rule: a single read
 * query, {@code SELECT} or {@code WITH … SELECT}, that changes nothing.
 * <p>
 * The checks look at the statement with its comments, string literals and quoted identifiers blanked
 * out, so a {@code ;} or a keyword inside them is not mistaken for code. They only guard the shape of
 * the statement: what it actually does is bounded by running it in a read-only transaction, see
 * {@link DqDataSourceProvider#queryReadOnly}.
 */
public final class DqCustomSql {

    /** What makes a statement unacceptable as the query of a rule. */
    public enum Problem {
        /** Nothing but whitespace and comments. */
        BLANK,
        /** More than one statement: a {@code ;} that is not the trailing one. */
        MULTIPLE_STATEMENTS,
        /** The statement does not start with {@code SELECT} or {@code WITH}. */
        NOT_SELECT,
        /**
         * A word that makes a read query write: a data-modifying CTE ({@code WITH d AS (DELETE …)}),
         * {@code SELECT … INTO}, or a locking {@code SELECT … FOR UPDATE}.
         */
        MODIFYING_KEYWORD
    }

    /** The outcome of {@link #check}: the problem found, and the offending word when there is one. */
    public record Violation(Problem problem, @Nullable String word) {
    }

    private static final Pattern FIRST_WORD = Pattern.compile("^[\\s(]*([A-Za-z]+)");

    private static final Pattern MODIFYING_WORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|INTO)\\b", Pattern.CASE_INSENSITIVE);

    /** A PostgreSQL dollar-quote opening tag: {@code $$} or {@code $tag$}. */
    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$[A-Za-z_]*\\$");

    private DqCustomSql() {
    }

    /**
     * The statement as it is run: trimmed, without the trailing {@code ;} a user habitually types,
     * which would break wrapping it into a sub-query.
     */
    public static String normalize(@Nullable String sql) {
        if (sql == null) {
            return "";
        }
        String masked = mask(sql);
        int end = masked.length();
        while (end > 0 && (Character.isWhitespace(masked.charAt(end - 1)) || masked.charAt(end - 1) == ';')) {
            end--;
        }
        return sql.substring(0, end).strip();
    }

    /**
     * Checks that a statement is a single read query.
     *
     * @return the first problem found, or null when the statement is acceptable
     */
    @Nullable
    public static Violation check(@Nullable String sql) {
        String statement = mask(normalize(sql));
        if (statement.isBlank()) {
            return new Violation(Problem.BLANK, null);
        }
        if (statement.indexOf(';') >= 0) {
            return new Violation(Problem.MULTIPLE_STATEMENTS, null);
        }
        Matcher first = FIRST_WORD.matcher(statement);
        String firstWord = first.find() ? first.group(1).toUpperCase(Locale.ROOT) : "";
        if (!firstWord.equals("SELECT") && !firstWord.equals("WITH")) {
            return new Violation(Problem.NOT_SELECT, null);
        }
        Matcher modifying = MODIFYING_WORD.matcher(statement);
        if (modifying.find()) {
            return new Violation(Problem.MODIFYING_KEYWORD, modifying.group(1).toUpperCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * The statement with comments, string literals, quoted identifiers and dollar-quoted strings
     * replaced by spaces. Keeps the length, so a position in the result is the same position in the
     * original. An unterminated literal or comment blanks out the rest of the statement.
     */
    static String mask(String sql) {
        StringBuilder masked = new StringBuilder(sql);
        int i = 0;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);
            int end;
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                end = sql.indexOf('\n', i);
                end = end < 0 ? length : end;
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                end = sql.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
            } else if (c == '\'' || c == '"' || c == '`') {
                end = quotedEnd(sql, i, c);
            } else if (c == '$') {
                Matcher tag = DOLLAR_TAG.matcher(sql).region(i, length);
                if (!tag.lookingAt()) {
                    i++;
                    continue;
                }
                end = sql.indexOf(tag.group(), tag.end());
                end = end < 0 ? length : end + tag.group().length();
            } else {
                i++;
                continue;
            }
            for (int j = i; j < end; j++) {
                masked.setCharAt(j, ' ');
            }
            i = end;
        }
        return masked.toString();
    }

    /** The position after the quote closing the one at {@code start}; a doubled quote is an escaped one. */
    private static int quotedEnd(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }
}
