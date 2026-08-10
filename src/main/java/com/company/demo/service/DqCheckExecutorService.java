package com.company.demo.service;

import com.company.demo.dto.DqRuleConfig;
import com.company.demo.dto.DqRuleFilter;
import com.company.demo.dto.DqRuleQueries;
import com.company.demo.dto.DqSqlQuery;
import com.company.demo.entity.DqCheckRun;
import com.company.demo.entity.DqCheckRunResult;
import com.company.demo.entity.DqIssue;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqCheckResultStatus;
import com.company.demo.enums.DqCheckRunStatus;
import com.company.demo.enums.DqIssueStatus;
import com.company.demo.enums.DqRuleType;
import com.company.demo.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.*;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.core.security.SystemAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Runs a data quality check: takes the rule filter of the "run check" page, records a
 * {@link DqCheckRun}, evaluates every matching {@link DqRule} against its data source and stores a
 * {@link DqCheckRunResult} per rule, opening a {@link DqIssue} for the rules that failed.
 * <p>
 * The run is asynchronous. {@link #startCheckRun(DqRuleFilter)} persists the run with status
 * {@link DqCheckRunStatus#RUNNING} and returns immediately; the rules are evaluated on a worker
 * thread, under the identity of the user who triggered the run, and the run is updated with the
 * totals when it ends. A run left in {@code RUNNING} therefore means the application died mid-run.
 * <p>
 * The SQL comes from {@link DqSqlQueryBuilder}: for each rule a metrics query, whose two counts give
 * the pass rate, and a samples query for the violating rows. A rule fails when its pass rate is below
 * the {@code threshold} of its configuration ({@value #DEFAULT_THRESHOLD}% when it sets none). A rule
 * that cannot be translated or executed is not fatal — it is recorded as
 * {@link DqCheckResultStatus#SKIPPED} with the reason and the run carries on.
 */
@Service
public class DqCheckExecutorService {

    private static final Logger log = LoggerFactory.getLogger(DqCheckExecutorService.class);

    /** Pass rate, in percent, a rule must reach when its configuration sets no threshold. */
    public static final double DEFAULT_THRESHOLD = 100d;

    /**
     * Hard cap on the sample rows kept for one result, applied on top of {@code ruleConfig.rowsLimit}:
     * the limit is optional in the configuration, and an unlimited samples query against a large
     * table would otherwise be read into memory in full.
     */
    private static final int MAX_SAMPLE_ROWS = 20;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATE_SCALE = 2;

    /** Reads the single row of a metrics query positionally: aliases are folded differently per database. */
    private static final ResultSetExtractor<Metrics> METRICS_EXTRACTOR = rs -> {
        if (!rs.next()) {
            return new Metrics(BigInteger.ZERO, BigInteger.ZERO);
        }
        return new Metrics(count(rs.getBigDecimal(1)), count(rs.getBigDecimal(2)));
    };

    private final DataManager dataManager;
    private final DqSqlQueryBuilder queryBuilder;
    private final DqDataSourceProvider dataSourceProvider;
    private final DqRuleFilterService ruleFilterService;
    private final CurrentAuthentication currentAuthentication;
    private final SystemAuthenticator systemAuthenticator;
    private final Messages messages;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public DqCheckExecutorService(DataManager dataManager,
                                  DqSqlQueryBuilder queryBuilder,
                                  DqDataSourceProvider dataSourceProvider,
                                  DqRuleFilterService ruleFilterService,
                                  CurrentAuthentication currentAuthentication,
                                  SystemAuthenticator systemAuthenticator,
                                  Messages messages,
                                  @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                                  TaskExecutor taskExecutor) {
        this.dataManager = dataManager;
        this.queryBuilder = queryBuilder;
        this.dataSourceProvider = dataSourceProvider;
        this.ruleFilterService = ruleFilterService;
        this.currentAuthentication = currentAuthentication;
        this.systemAuthenticator = systemAuthenticator;
        this.messages = messages;
        this.taskExecutor = taskExecutor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Records a check run for the rules matching the filter and starts evaluating them in the
     * background.
     *
     * @return the persisted run, with status {@link DqCheckRunStatus#RUNNING} — the totals are filled
     * in later, so reload it to observe the outcome
     * @throws IllegalArgumentException when the filter selects no data source or matches no rule
     */
    public DqCheckRun startCheckRun(DqRuleFilter filter) {
        if (filter == null || !StringUtils.hasText(filter.getDataSource())) {
            throw new IllegalArgumentException("Data source is not selected");
        }

        List<DqRule> rules = loadRules(filter);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("No rules match the filter");
        }

        DqCheckRun run = dataManager.create(DqCheckRun.class);
        run.setDataSource(filter.getDataSource());
        run.setRulesTotal(rules.size());
        run.setTriggeredUsername(currentAuthentication.getUser().getUsername());
        run.setStartedAt(LocalDateTime.now());
        run.setStatus(DqCheckRunStatus.RUNNING);
        run.setRulesPassed(0);
        run.setRulesFailed(0);
        run.setRulesSkipped(0);

        DqCheckRun savedRun = dataManager.save(run);

        UUID runId = savedRun.getId();
        String username = savedRun.getTriggeredUsername();
        // ids, not entities: the worker thread loads its own instances with the fetch plan it needs
        List<UUID> ruleIds = rules.stream().map(DqRule::getId).toList();

        // the security context does not follow a task onto the worker thread, so it is re-established
        // there as the user who triggered the run
        taskExecutor.execute(() ->
                systemAuthenticator.runWithUser(username, () -> executeCheckRun(runId, ruleIds)));

        return savedRun;
    }

    /**
     * Body of a check run: evaluates the rules one by one and closes the run with the totals.
     * Runs on a worker thread — every failure is turned into stored state, nothing is rethrown to
     * a caller that no longer exists.
     */
    void executeCheckRun(UUID checkRunId, List<UUID> ruleIds) {
        DqCheckRun run;
        try {
            run = dataManager.load(DqCheckRun.class).id(checkRunId).one();
        } catch (Exception e) {
            log.error("Check run {} cannot be loaded, the run is abandoned", checkRunId, e);
            return;
        }

        int passed = 0;
        int failed = 0;
        int skipped = 0;
        try {
            for (UUID ruleId : ruleIds) {
                DqCheckRunResult result = executeRule(run, ruleId);
                switch (result.getStatus()) {
                    case PASSED -> passed++;
                    case FAILED -> failed++;
                    default -> skipped++;
                }
            }
            finishRun(run, DqCheckRunStatus.SUCCESS, passed, failed, skipped, null);
        } catch (Exception e) {
            // a rule of its own never gets here (executeRule absorbs it); this is the check run
            // itself failing, e.g. the results cannot be stored
            log.error("Check run {} failed", checkRunId, e);
            finishRun(run, DqCheckRunStatus.FAILED, passed, failed, skipped, describe(e));
        }
    }

    /**
     * Evaluates one rule and stores its result. A rule that cannot be evaluated is recorded as
     * skipped instead of aborting the run: one unsupported or misconfigured rule must not cost the
     * results of all the others.
     */
    private DqCheckRunResult executeRule(DqCheckRun run, UUID ruleId) {
        DqRule rule = dataManager.load(DqRule.class)
                .id(ruleId)
                // owner is needed to assign the issue a failing rule opens
                .fetchPlan(builder -> builder.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.LOCAL))
                .one();

        DqRuleType ruleType = rule.getRuleType();
        if (ruleType == null) {
            return saveResult(skippedResult(run, rule, "Rule type is not set"));
        }

        try {
            return switch (ruleType) {
                case NOT_NULL -> executeCheckNotNull(run, rule);
                case UNIQUENESS -> executeCheckUniqueness(run, rule);
                case REGEXP -> executeCheckRegexp(run, rule);
                case RANGE_NUMBER -> executeCheckRangeNumber(run, rule);
                case RANGE_DATE -> executeCheckRangeDate(run, rule);
                case REFERENTIAL -> executeCheckReferential(run, rule);
                case CUSTOM_SQL -> executeCheckCustomSql(run, rule);
                case CROSS_SOURCE_AGGREGATED -> executeCheckCrossSourceAggregated(run, rule);
            };
        } catch (Exception e) {
            log.warn("Rule '{}' ({}) is skipped in check run {}", rule.getName(), ruleId, run.getId(), e);
            return saveResult(skippedResult(run, rule, describe(e)));
        }
    }

    // ---------------------------------------------------------------------
    // per rule type
    // ---------------------------------------------------------------------
    //
    // Every implemented type is measured the same way — the query builder folds the difference
    // between them into the violation predicate of the metrics and samples queries. The types keep
    // a method of their own so that a type needing more than a row count (a second data source, a
    // comparison the database cannot make) has a place to grow into.

    /** Counts the rows whose column holds no value. */
    private DqCheckRunResult executeCheckNotNull(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /** Counts the rows whose column value occurs more than once in the table. */
    private DqCheckRunResult executeCheckUniqueness(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /** Counts the rows whose column value the configured pattern does not match. */
    private DqCheckRunResult executeCheckRegexp(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /** Counts the rows whose numeric column value falls outside the configured bounds. */
    private DqCheckRunResult executeCheckRangeNumber(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /** Counts the rows whose date column value falls outside the configured bounds. */
    private DqCheckRunResult executeCheckRangeDate(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /**
     * Counts the rows whose column value has no match in the referenced table. The query builder does
     * not translate this type yet, so the rule is recorded as skipped until it does.
     */
    private DqCheckRunResult executeCheckReferential(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /**
     * Counts the rows the user-supplied statement reports as violations. The query builder does not
     * translate this type yet, so the rule is recorded as skipped until it does.
     */
    private DqCheckRunResult executeCheckCustomSql(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    /**
     * Compares an aggregate of the rule's data source against the same aggregate of a second one.
     * The query builder does not translate this type yet, so the rule is recorded as skipped until
     * it does.
     * <p>
     * TODO this type will not fit {@link #executeRowLevelCheck}: it needs the second query run
     *  against {@code ruleConfig.secondaryDataSourceId} and the two results compared here.
     */
    private DqCheckRunResult executeCheckCrossSourceAggregated(DqCheckRun run, DqRule rule) {
        return executeRowLevelCheck(run, rule);
    }

    // ---------------------------------------------------------------------
    // measurement
    // ---------------------------------------------------------------------

    /**
     * Measures a rule that can point at individual violating rows: runs the metrics query, derives
     * the pass rate, and collects sample rows for a rule that failed.
     */
    private DqCheckRunResult executeRowLevelCheck(DqCheckRun run, DqRule rule) {
        DqRuleQueries queries = queryBuilder.buildQueries(rule);
        JdbcTemplate jdbcTemplate = dataSourceProvider.getJdbcTemplate(rule.getDataSource());
        DqSqlQuery metricsQuery = queries.metrics();

        long startedAt = System.currentTimeMillis();

        Metrics metrics = jdbcTemplate.query(metricsQuery.sql(), METRICS_EXTRACTOR, metricsQuery.paramArray());
        BigDecimal passRate = passRate(metrics);
        boolean failed = passRate.compareTo(BigDecimal.valueOf(threshold(rule))) < 0;
        // sampling costs a second query, so it is only paid for when there is something to show
        String sampleViolations = failed && queries.samples() != null
                ? loadSamples(jdbcTemplate, queries.samples())
                : null;

        long executionMs = System.currentTimeMillis() - startedAt;

        DqCheckRunResult result = dataManager.create(DqCheckRunResult.class);
        result.setCheckRun(run);
        result.setRule(rule);
        result.setStatus(failed ? DqCheckResultStatus.FAILED : DqCheckResultStatus.PASSED);
        result.setTotalRecords(metrics.total());
        result.setFailedRecords(metrics.failed());
        result.setPassRate(passRate);
        result.setExecutionMs(executionMs);
        result.setSampleViolations(sampleViolations);
        result.setExecutedQuery(metricsQuery.toDisplayString());

        return saveResult(result);
    }

    /**
     * Reads the violating rows as a JSON array of objects keyed by column label.
     *
     * @return the JSON text, or {@code null} when the query returned no row
     */
    @Nullable
    private String loadSamples(JdbcTemplate jdbcTemplate, DqSqlQuery samplesQuery) {
        List<Map<String, Object>> rows = jdbcTemplate.query(samplesQuery.sql(), rs -> {
            List<Map<String, Object>> result = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (result.size() < MAX_SAMPLE_ROWS && rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), jsonValue(rs.getObject(i)));
                }
                result.add(row);
            }
            return result;
        }, samplesQuery.paramArray());

        if (rows == null || rows.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            // the samples are a diagnostic aid, not the measurement: losing them must not fail the rule
            log.warn("Sample violations cannot be serialized", e);
            return null;
        }
    }

    /**
     * Narrows a value read from an arbitrary column to something JSON can carry. Anything that is not
     * a number, a boolean or a string — a timestamp, an array, a database-specific object — is stored
     * as its text form rather than mapped structurally.
     */
    @Nullable
    private Object jsonValue(@Nullable Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------------
    // results and issues
    // ---------------------------------------------------------------------

    /**
     * Stores a result and, for a failed one, the issue it raises. Both are saved in a single call so
     * that a result never appears without the issue it opened.
     */
    private DqCheckRunResult saveResult(DqCheckRunResult result) {
        if (result.getStatus() != DqCheckResultStatus.FAILED) {
            return dataManager.save(result);
        }
        EntitySet saved = dataManager.save(result, issueFor(result));
        return saved.get(result);
    }

    private DqCheckRunResult skippedResult(DqCheckRun run, DqRule rule, String reason) {
        DqCheckRunResult result = dataManager.create(DqCheckRunResult.class);
        result.setCheckRun(run);
        result.setRule(rule);
        result.setStatus(DqCheckResultStatus.SKIPPED);
        result.setErrorMessage(reason);
        return result;
    }

    /**
     * The issue a failed result raises: the one already open for the same rule and data source when
     * there is one, refreshed with the latest numbers, otherwise a new one. Re-running a check that
     * keeps failing therefore keeps a single issue instead of a new one per run.
     */
    private DqIssue issueFor(DqCheckRunResult result) {
        DqRule rule = result.getRule();
        DqIssue issue = dataManager.load(DqIssue.class)
                .query("select i from demo_DqIssue i" +
                        " where i.rule = :rule and i.dataSource = :dataSource and i.status = :status" +
                        " order by i.createdAt desc")
                .parameter("rule", rule)
                .parameter("dataSource", rule.getDataSource())
                .parameter("status", DqIssueStatus.OPEN.getId())
                .maxResults(1)
                .optional()
                .orElseGet(() -> newIssue(rule));

        issue.setCheckResult(result);
        issue.setSeverity(rule.getSeverity());
        issue.setAffectedRows(result.getFailedRecords());
        issue.setTitle(issueTitle(rule));
        issue.setDescription(issueDescription(rule, result));
        issue.setUpdatedAt(LocalDateTime.now());
        return issue;
    }

    private DqIssue newIssue(DqRule rule) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setDataSource(rule.getDataSource());
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setCreatedAt(LocalDateTime.now());
        return issue;
    }

    private String issueTitle(DqRule rule) {
        return messages.formatMessage(DqCheckExecutorService.class, "dqCheckExecutor.issueTitle",
                rule.getName(), target(rule));
    }

    private String issueDescription(DqRule rule, DqCheckRunResult result) {
        return messages.formatMessage(DqCheckExecutorService.class, "dqCheckExecutor.issueDescription",
                result.getPassRate(), BigDecimal.valueOf(threshold(rule)).setScale(RATE_SCALE, RoundingMode.HALF_UP),
                result.getFailedRecords(), result.getTotalRecords(), target(rule));
    }

    /** The table, and the column when the rule has one, as shown to the user. */
    private String target(DqRule rule) {
        String table = rule.getTableName() == null ? "" : rule.getTableName();
        return rule.getColumnName() == null ? table : table + "." + rule.getColumnName();
    }

    /**
     * Closes the run with its totals. The score is the share of the rules that passed among those
     * actually evaluated, so that skipped rules neither reward nor penalize the run.
     */
    private void finishRun(DqCheckRun run, DqCheckRunStatus status,
                           int passed, int failed, int skipped, @Nullable String errorMessage) {
        run.setStatus(status);
        run.setRulesPassed(passed);
        run.setRulesFailed(failed);
        run.setRulesSkipped(skipped);
        run.setFinishedAt(LocalDateTime.now());
        run.setDqScore(dqScore(passed, passed + failed));
        run.setErrorMessage(errorMessage);
        dataManager.save(run);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private List<DqRule> loadRules(DqRuleFilter filter) {
        return dataManager.load(DqRule.class)
                .query("select e from demo_DqRule e")
                .condition(ruleFilterService.createRuleCondition())
                .parameters(ruleFilterService.createRuleParameters(filter))
                .sort(Sort.by("name"))
                .list();
    }

    /**
     * The share of non-violating rows, in percent. A table with no rows has nothing to violate the
     * rule and scores 100%.
     */
    private BigDecimal passRate(Metrics metrics) {
        if (metrics.total().signum() == 0) {
            return HUNDRED.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        BigInteger passedRows = metrics.total().subtract(metrics.failed()).max(BigInteger.ZERO);
        return new BigDecimal(passedRows)
                .multiply(HUNDRED)
                .divide(new BigDecimal(metrics.total()), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal dqScore(int passed, int evaluated) {
        if (evaluated == 0) {
            return null;
        }
        return BigDecimal.valueOf(passed)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(evaluated), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private double threshold(DqRule rule) {
        DqRuleConfig config = JsonUtils.parseConfig(rule.getRuleConfig(), DqRuleConfig.class, objectMapper);
        if (config == null || config.getThreshold() == null) {
            return DEFAULT_THRESHOLD;
        }
        return config.getThreshold();
    }

    private static BigInteger count(@Nullable BigDecimal value) {
        return value == null ? BigInteger.ZERO : value.toBigInteger();
    }

    /** Message stored on a skipped result or a failed run: exceptions such as NPE carry none of their own. */
    private String describe(Exception e) {
        return StringUtils.hasText(e.getMessage())
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }

    /** The two counts of a metrics query: rows examined, and rows violating the rule. */
    private record Metrics(BigInteger total, BigInteger failed) {
    }
}
