package uz.kapitalbank.umida.service.dq;

import io.jmix.core.DataManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Reads the rows that violated a rule, by running the {@code violationsQuery} the check run stored on
 * the {@link DqCheckRunResult} against the data source of the run. The rows are read live, one page
 * at a time: they show the data as it is now, which may have changed since the check.
 * <p>
 * The query is only ever taken from the stored result, never from the caller, and must be a
 * {@code SELECT}. How it is paged depends on the type of the rule: a generated query ends in its own
 * {@code ORDER BY} and takes the page clause as it is; the query a user wrote for a
 * {@link DqRuleType#CUSTOM_SQL} rule may end in anything (a {@code LIMIT}, a comment), so it is paged
 * as a sub-query, and runs read-only like the rest of that rule.
 */
@Service
public class DqViolationsService {

    private static final String SELECT = "SELECT";
    private static final String QUERY_ALIAS = "v";
    /** How many rows a streamed read takes from the database at a time. */
    private static final int STREAM_FETCH_SIZE = 1000;

    private final DataManager dataManager;
    private final DqDataSourceProvider dataSourceProvider;

    public DqViolationsService(DataManager dataManager, DqDataSourceProvider dataSourceProvider) {
        this.dataManager = dataManager;
        this.dataSourceProvider = dataSourceProvider;
    }

    /**
     * Counts the rows the violations query of the result currently returns.
     *
     * @throws IllegalStateException when the result has no violations query to run
     */
    public long countViolations(UUID checkResultId) {
        ViolationsQuery query = loadQuery(checkResultId);
        Long count = run(query, "SELECT COUNT(*) FROM (" + query.subQuery() + ") " + QUERY_ALIAS,
                rs -> rs.next() ? rs.getLong(1) : 0L);
        return count == null ? 0 : count;
    }

    /**
     * Reads a page of the rows the violations query of the result currently returns. The columns are
     * reported even for an empty page.
     *
     * @param firstResult zero-based index of the first row
     * @param maxResults  the page size
     * @throws IllegalStateException when the result has no violations query to run
     */
    public ViolationsPage loadViolations(UUID checkResultId, int firstResult, int maxResults) {
        ViolationsQuery query = loadQuery(checkResultId);
        return run(query, pageSql(query, firstResult, maxResults), PAGE_EXTRACTOR);
    }

    /**
     * Reads the rows the violations query of the result currently returns, up to {@code maxResults},
     * handing them to the handler one by one as the data source streams them: unlike a page, the rows
     * are never all held in memory. The columns are reported first, even when there are no rows.
     *
     * @return the number of rows handed to the handler
     * @throws IllegalStateException when the result has no violations query to run
     */
    public long readViolations(UUID checkResultId, int maxResults, ViolationsHandler handler) {
        ViolationsQuery query = loadQuery(checkResultId);
        ResultSetExtractor<Long> extractor = rs -> {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            handler.columns(columnLabels(metaData));

            long rowCount = 0;
            while (rs.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = rs.getObject(i);
                }
                handler.row(Arrays.asList(row));
                rowCount++;
            }
            return rowCount;
        };
        Long rowCount = dataSourceProvider.queryStreaming(query.dataSource(), pageSql(query, 0, maxResults),
                extractor, STREAM_FETCH_SIZE);
        return rowCount == null ? 0 : rowCount;
    }

    /**
     * The violations query limited to a page. A generated query takes the page clause after its own
     * {@code ORDER BY}; a user-written one is paged as a sub-query.
     */
    private static String pageSql(ViolationsQuery query, int firstResult, int maxResults) {
        String pageClause = query.dialect().pageClause(firstResult, maxResults);
        return query.userWritten()
                ? "SELECT * FROM (" + query.subQuery() + ") " + QUERY_ALIAS + " " + pageClause
                : query.sql() + " " + pageClause;
    }

    /** Reads the columns positionally: a query may select two columns under the same label. */
    private static final ResultSetExtractor<ViolationsPage> PAGE_EXTRACTOR = rs -> {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = columnLabels(metaData);

        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                row[i - 1] = rs.getObject(i);
            }
            rows.add(Arrays.asList(row));
        }
        return new ViolationsPage(columns, rows);
    };

    private static List<String> columnLabels(ResultSetMetaData metaData) throws SQLException {
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        return columns;
    }

    private ViolationsQuery loadQuery(UUID checkResultId) {
        DqCheckRunResult result = dataManager.load(DqCheckRunResult.class)
                .id(checkResultId)
                .fetchPlan(fp -> fp.add("violationsQuery")
                        .add("dataSource")
                        .add("rule", rule -> rule.add("ruleType")))
                .one();

        String sql = result.getViolationsQuery();
        if (!StringUtils.hasText(sql)) {
            throw new IllegalStateException("Check result " + checkResultId + " has no violations query");
        }
        boolean userWritten = result.getRule() != null && result.getRule().getRuleType() == DqRuleType.CUSTOM_SQL;
        if (userWritten) {
            sql = DqCustomSql.normalize(sql);
            if (DqCustomSql.check(sql) != null) {
                throw new IllegalStateException("Violations query of check result " + checkResultId
                        + " is not a single read query");
            }
        } else {
            sql = sql.strip();
            if (!sql.regionMatches(true, 0, SELECT, 0, SELECT.length())) {
                throw new IllegalStateException("Violations query of check result " + checkResultId
                        + " is not a SELECT statement");
            }
        }

        String dataSource = result.getDataSource();
        if (!StringUtils.hasText(dataSource)) {
            throw new IllegalStateException("Check result " + checkResultId + " has no data source");
        }
        return new ViolationsQuery(sql, userWritten, dataSource,
                dataSourceProvider.resolveDialect(dataSource),
                dataSourceProvider.getJdbcTemplate(dataSource));
    }

    private record ViolationsQuery(String sql, boolean userWritten, String dataSource, DqSqlDialect dialect,
                                   JdbcTemplate jdbcTemplate) {

        /**
         * The query as the body of a sub-query. A user-written one goes on lines of its own, so that a
         * trailing line comment cannot swallow the closing parenthesis.
         */
        String subQuery() {
            return userWritten ? "\n" + sql + "\n" : sql;
        }
    }

    /** Runs a statement built around the violations query; a user-written one runs read-only. */
    private <T> T run(ViolationsQuery query, String statement, ResultSetExtractor<T> extractor) {
        return query.userWritten()
                ? dataSourceProvider.queryReadOnly(query.dataSource(), statement, extractor, 0)
                : query.jdbcTemplate().query(statement, extractor);
    }

    /**
     * A page of violating rows.
     *
     * @param columns the column labels, in the order the query selected them
     * @param rows    the values of each row, positionally matching {@code columns}; values may be
     *                {@code null}
     */
    public record ViolationsPage(List<String> columns, List<List<Object>> rows) {
    }

    /** Takes the violating rows of {@link #readViolations} as they are read. */
    public interface ViolationsHandler {

        /** The column labels, in the order the query selected them; called once, before any row. */
        void columns(List<String> columns);

        /** The values of a row, positionally matching the columns; values may be {@code null}. */
        void row(List<Object> values);
    }
}
