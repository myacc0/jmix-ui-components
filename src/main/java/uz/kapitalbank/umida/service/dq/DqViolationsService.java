package uz.kapitalbank.umida.service.dq;

import io.jmix.core.DataManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;

import java.sql.ResultSetMetaData;
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
 * {@code SELECT}.
 */
@Service
public class DqViolationsService {

    private static final String SELECT = "SELECT";
    private static final String QUERY_ALIAS = "v";

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
        Long count = query.jdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM (" + query.sql() + ") " + QUERY_ALIAS, Long.class);
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
        String pageSql = query.sql() + " " + query.dialect().pageClause(firstResult, maxResults);
        return query.jdbcTemplate().query(pageSql, PAGE_EXTRACTOR);
    }

    /** Reads the columns positionally: a query may select two columns under the same label. */
    private static final ResultSetExtractor<ViolationsPage> PAGE_EXTRACTOR = rs -> {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }

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

    private ViolationsQuery loadQuery(UUID checkResultId) {
        DqCheckRunResult result = dataManager.load(DqCheckRunResult.class)
                .id(checkResultId)
                .fetchPlan(fp -> fp.add("violationsQuery")
                        .add("checkRun", checkRun -> checkRun.add("dataSource")))
                .one();

        String sql = result.getViolationsQuery();
        if (!StringUtils.hasText(sql)) {
            throw new IllegalStateException("Check result " + checkResultId + " has no violations query");
        }
        sql = sql.strip();
        if (!sql.regionMatches(true, 0, SELECT, 0, SELECT.length())) {
            throw new IllegalStateException("Violations query of check result " + checkResultId
                    + " is not a SELECT statement");
        }

        String dataSource = result.getCheckRun().getDataSource();
        return new ViolationsQuery(sql,
                dataSourceProvider.resolveDialect(dataSource),
                dataSourceProvider.getJdbcTemplate(dataSource));
    }

    private record ViolationsQuery(String sql, DqSqlDialect dialect, JdbcTemplate jdbcTemplate) {
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
}
