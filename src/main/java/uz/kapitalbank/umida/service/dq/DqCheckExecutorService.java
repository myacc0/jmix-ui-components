package uz.kapitalbank.umida.service.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleConfig;
import uz.kapitalbank.umida.dto.dq.DqRuleFilter;
import uz.kapitalbank.umida.dto.dq.DqRuleQueries;
import uz.kapitalbank.umida.dto.dq.DqSqlQuery;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.DqCheckResultStatus;
import uz.kapitalbank.umida.enums.dq.DqCheckRunStatus;
import uz.kapitalbank.umida.enums.dq.DqCheckRunTrigger;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.enums.dq.DqSqlDialect;
import uz.kapitalbank.umida.utils.JsonUtils;
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
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Runs a data quality check: takes the rule filter of the "run check" page, records a
 * {@link DqCheckRun}, evaluates every matching {@link DqRule} against its data source and stores a
 * {@link DqCheckRunResult} per rule, and keeps the {@link DqIssue}s of the rules in step with the
 * results ({@link DqIssueService#issuesFor}).
 * <p>
 * The run is asynchronous. {@link #startCheckRun(DqRuleFilter)} persists the run with status
 * {@link DqCheckRunStatus#RUNNING} and returns immediately; the rules are evaluated on a worker
 * thread, under the identity of the user who triggered the run, and the run is updated with the
 * totals when it ends. A run left in {@code RUNNING} therefore means the application died mid-run.
 * <p>
 * The SQL comes from {@link DqSqlQueryBuilder}: for each rule a metrics query, whose two counts give
 * the pass rate, and a violations query selecting the violating rows. The violations query is not run
 * here: it is stored on the result, with its parameters inlined, and run page by page when a user
 * looks at the violating rows ({@link DqViolationsService}). A rule fails when its pass rate is below
 * the {@code threshold} of its configuration ({@value #DEFAULT_THRESHOLD}% when it sets none). A rule
 * that cannot be translated or executed is not fatal — it is recorded as
 * {@link DqCheckResultStatus#SKIPPED} with the reason and the run carries on.
 */
@Service
public class DqCheckExecutorService {

    private static final Logger log = LoggerFactory.getLogger(DqCheckExecutorService.class);

    /** Pass rate, in percent, a rule must reach when its configuration sets no threshold. */
    public static final double DEFAULT_THRESHOLD = 100d;

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
    private final DqCheckRunService checkRunService;
    private final DqIssueService issueService;
    private final CurrentAuthentication currentAuthentication;
    private final SystemAuthenticator systemAuthenticator;
    private final Messages messages;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public DqCheckExecutorService(DataManager dataManager,
                                  DqSqlQueryBuilder queryBuilder,
                                  DqDataSourceProvider dataSourceProvider,
                                  DqRuleFilterService ruleFilterService,
                                  DqCheckRunService checkRunService,
                                  DqIssueService issueService,
                                  CurrentAuthentication currentAuthentication,
                                  SystemAuthenticator systemAuthenticator,
                                  Messages messages,
                                  @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                                  TaskExecutor taskExecutor) {
        this.dataManager = dataManager;
        this.queryBuilder = queryBuilder;
        this.dataSourceProvider = dataSourceProvider;
        this.ruleFilterService = ruleFilterService;
        this.checkRunService = checkRunService;
        this.issueService = issueService;
        this.currentAuthentication = currentAuthentication;
        this.systemAuthenticator = systemAuthenticator;
        this.messages = messages;
        this.taskExecutor = taskExecutor;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Records a check run for the rules matching the filter and starts evaluating them in the
     * background. The run is a {@link DqCheckRunTrigger#MANUAL} one, triggered by the employee the
     * current user is linked to — none for a user without an employee, such as {@code admin}.
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

        String username = currentAuthentication.getUser().getUsername();

        DqCheckRun run = dataManager.create(DqCheckRun.class);
        run.setDataSource(filter.getDataSource());
        run.setRulesTotal(rules.size());
        run.setTriggered(DqCheckRunTrigger.MANUAL);
        run.setTriggeredBy(checkRunService.findEmployeeOfUser(username).orElse(null));
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(DqCheckRunStatus.RUNNING);
        run.setRulesPassed(0);
        run.setRulesFailed(0);
        run.setRulesSkipped(0);

        DqCheckRun savedRun = dataManager.save(run);

        UUID runId = savedRun.getId();
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
                .fetchPlan(FetchPlan.BASE)
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
    // between them into the violation predicate of the metrics and violations queries. The types keep
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
     * the pass rate, and stores the query selecting the violating rows next to the numbers.
     */
    private DqCheckRunResult executeRowLevelCheck(DqCheckRun run, DqRule rule) {
        DqSqlDialect dialect = dataSourceProvider.resolveDialect(rule.getDataSource());
        DqRuleQueries queries = queryBuilder.buildQueries(rule, dialect);
        JdbcTemplate jdbcTemplate = dataSourceProvider.getJdbcTemplate(rule.getDataSource());
        DqSqlQuery metricsQuery = queries.metrics();

        long startedAt = System.currentTimeMillis();

        Metrics metrics = jdbcTemplate.query(metricsQuery.sql(), METRICS_EXTRACTOR, metricsQuery.paramArray());
        BigDecimal passRate = passRate(metrics);
        boolean failed = passRate.compareTo(BigDecimal.valueOf(threshold(rule))) < 0;

        long executionMs = System.currentTimeMillis() - startedAt;

        DqCheckRunResult result = dataManager.create(DqCheckRunResult.class);
        result.setCheckRun(run);
        result.setRule(rule);
        result.setStatus(failed ? DqCheckResultStatus.FAILED : DqCheckResultStatus.PASSED);
        result.setTotalRecords(metrics.total());
        result.setFailedRecords(metrics.failed());
        result.setPassRate(passRate);
        result.setExecutionMs(executionMs);
        result.setExecutedQuery(metricsQuery.toDisplayString(dialect));
        if (queries.violations() != null) {
            // run later against the same data source, outside of this rule's context: the
            // parameters are inlined so that the stored text is complete on its own
            result.setViolationsQuery(queries.violations().toDisplayString(dialect));
        }

        return saveResult(result);
    }

    // ---------------------------------------------------------------------
    // results and issues
    // ---------------------------------------------------------------------

    /**
     * Stores a result together with the issues it opens, refreshes or resolves, in a single call, so
     * that a result never appears without the issue it opened.
     */
    private DqCheckRunResult saveResult(DqCheckRunResult result) {
        String failureDescription = result.getStatus() == DqCheckResultStatus.FAILED
                ? issueDescription(result.getRule(), result)
                : null;

        SaveContext saveContext = new SaveContext().saving(result);
        issueService.issuesFor(result, failureDescription).forEach(saveContext::saving);
        return dataManager.save(saveContext).get(result);
    }

    private DqCheckRunResult skippedResult(DqCheckRun run, DqRule rule, String reason) {
        DqCheckRunResult result = dataManager.create(DqCheckRunResult.class);
        result.setCheckRun(run);
        result.setRule(rule);
        result.setStatus(DqCheckResultStatus.SKIPPED);
        result.setErrorMessage(reason);
        return result;
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
        run.setFinishedAt(OffsetDateTime.now());
        run.setDqScore(dqScore(passed, passed + failed));
        run.setErrorMessage(errorMessage);
        dataManager.save(run);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private List<DqRule> loadRules(DqRuleFilter filter) {
        return dataManager.load(DqRule.class)
                .query("select e from umida_DqRule e")
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
