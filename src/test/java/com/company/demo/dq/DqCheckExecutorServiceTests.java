package com.company.demo.dq;

import com.company.demo.dto.DqRuleFilter;
import com.company.demo.entity.DqCheckRun;
import com.company.demo.entity.DqCheckRunResult;
import com.company.demo.entity.DqDataDomain;
import com.company.demo.entity.DqIssue;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqCheckResultStatus;
import com.company.demo.enums.DqCheckRunStatus;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqIssueStatus;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import com.company.demo.service.DqCheckExecutorService;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises a real check run end to end against the main store: the rules are evaluated over
 * {@code demo_dq_data_domain}, the table behind {@link DqDataDomain}, so the SQL the builder
 * produces is actually executed.
 * <p>
 * The rules are tied to a domain created by the test and the filter selects that domain, so a run
 * covers exactly the rules of the test case and nothing a developer left in the database. Row counts
 * are never asserted for the same reason — only the outcome that the test data guarantees: the
 * inserted domain has no parent, so a "parent is set" rule always finds at least one violation.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckExecutorServiceTests {

    private static final String TABLE = "demo_dq_data_domain";
    private static final long RUN_TIMEOUT_MS = 60_000;

    @Autowired
    DqCheckExecutorService checkExecutorService;

    @Autowired
    DataManager dataManager;

    @Autowired
    Metadata metadata;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<DqCheckRun> checkRuns = new ArrayList<>();

    private DqDataDomain domain;
    private DqRule passingRule;
    private DqRule failingRule;
    private DqRule unsupportedRule;

    @BeforeEach
    void setUp() {
        domain = createDomain();
        // "name" is a NOT NULL column, so no row can violate the rule
        passingRule = createRule("passing", DqRuleType.NOT_NULL, "name", "{}");
        // the domain created above has no parent, so this rule always finds a violation
        failingRule = createRule("failing", DqRuleType.NOT_NULL, "parent_id", "{\"rowsLimit\": 5}");
        // the query builder does not translate this type yet
        unsupportedRule = createRule("unsupported", DqRuleType.CUSTOM_SQL, "name",
                "{\"sql\": \"select 1\"}");
    }

    @Test
    void runsEveryRuleAndClosesTheRunWithItsTotals() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));

        assertEquals(DqCheckRunStatus.SUCCESS, run.getStatus());
        assertNull(run.getErrorMessage());
        assertNotNull(run.getFinishedAt());
        assertEquals("main", run.getDataSource());
        assertEquals("admin", run.getTriggeredUsername());

        assertEquals(3, run.getRulesTotal());
        assertEquals(1, run.getRulesPassed());
        assertEquals(1, run.getRulesFailed());
        assertEquals(1, run.getRulesSkipped());
        // the skipped rule is left out of the score: 1 of the 2 evaluated rules passed
        assertEquals(0, new BigDecimal("50.00").compareTo(run.getDqScore()));
    }

    @Test
    void storesAResultPerRule() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));
        List<DqCheckRunResult> results = loadResults(run);
        assertEquals(3, results.size());

        DqCheckRunResult passed = resultOf(results, passingRule);
        assertEquals(DqCheckResultStatus.PASSED, passed.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(passed.getPassRate()));
        assertEquals(BigInteger.ZERO, passed.getFailedRecords());
        assertNotNull(passed.getTotalRecords());
        assertNotNull(passed.getExecutedQuery());
        assertNotNull(passed.getExecutionMs());
        // nothing violated the rule, so there is nothing to sample
        assertNull(passed.getSampleViolations());

        DqCheckRunResult failed = resultOf(results, failingRule);
        assertEquals(DqCheckResultStatus.FAILED, failed.getStatus());
        assertTrue(failed.getFailedRecords().signum() > 0,
                "the domain created by the test has no parent, so the rule must find a violation");
        assertTrue(failed.getPassRate().compareTo(new BigDecimal("100.00")) < 0);

        JsonNode samples = readSamples(failed);
        assertTrue(samples.isArray());
        assertTrue(samples.size() > 0);
        assertTrue(samples.size() <= 5, "rowsLimit of the rule configuration caps the samples");

        DqCheckRunResult skipped = resultOf(results, unsupportedRule);
        assertEquals(DqCheckResultStatus.SKIPPED, skipped.getStatus());
        assertNotNull(skipped.getErrorMessage());
        assertTrue(skipped.getErrorMessage().contains(DqRuleType.CUSTOM_SQL.getId()),
                "the reason must name the rule type that is not supported: " + skipped.getErrorMessage());
        assertNull(skipped.getPassRate());
    }

    @Test
    void opensAnIssueForAFailedRuleOnly() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));

        assertTrue(loadIssues(passingRule).isEmpty());

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size());

        DqIssue issue = issues.get(0);
        assertEquals(DqIssueStatus.OPEN, issue.getStatus());
        assertEquals(DqSeverity.HIGH, issue.getSeverity());
        assertEquals("main", issue.getDataSource());
        assertNotNull(issue.getCreatedAt());
        assertTrue(issue.getTitle().contains(failingRule.getName()));
        assertNotNull(issue.getDescription());

        DqCheckRunResult failed = resultOf(loadResults(run), failingRule);
        assertEquals(failed.getFailedRecords(), issue.getAffectedRows());
        assertEquals(failed.getId(), issue.getCheckResult().getId());
    }

    @Test
    void reusesTheOpenIssueOnTheNextRun() {
        awaitFinished(checkExecutorService.startCheckRun(filter()));
        UUID firstIssueId = loadIssues(failingRule).get(0).getId();

        DqCheckRun secondRun = awaitFinished(checkExecutorService.startCheckRun(filter()));

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size(), "the rule keeps failing, so the open issue must be reused");
        assertEquals(firstIssueId, issues.get(0).getId());
        // the issue now points at the newest result
        assertEquals(resultOf(loadResults(secondRun), failingRule).getId(),
                issues.get(0).getCheckResult().getId());
        assertNotNull(issues.get(0).getUpdatedAt());
    }

    @Test
    void rejectsAFilterThatMatchesNothing() {
        DqRuleFilter filter = filter();
        filter.setTableName("no_such_table_" + UUID.randomUUID().toString().replace("-", ""));

        assertThrows(IllegalArgumentException.class, () -> checkExecutorService.startCheckRun(filter));
    }

    @Test
    void rejectsAFilterWithoutDataSource() {
        DqRuleFilter filter = metadata.create(DqRuleFilter.class);

        assertThrows(IllegalArgumentException.class, () -> checkExecutorService.startCheckRun(filter));
    }

    // ---------------------------------------------------------------------
    // test data
    // ---------------------------------------------------------------------

    private DqRuleFilter filter() {
        DqRuleFilter filter = metadata.create(DqRuleFilter.class);
        filter.setDataSource("main");
        // the domain is created by this test, so only its own rules are selected
        filter.setDomain(domain);
        return filter;
    }

    private DqDataDomain createDomain() {
        DqDataDomain dataDomain = dataManager.create(DqDataDomain.class);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        dataDomain.setCode("dq-test-" + suffix);
        dataDomain.setShortName("DQ test " + suffix);
        dataDomain.setName("DQ executor test domain " + suffix);
        dataDomain.setCreatedAt(LocalDateTime.now());
        return dataManager.save(dataDomain);
    }

    private DqRule createRule(String name, DqRuleType ruleType, String columnName, String ruleConfig) {
        DqRule rule = dataManager.create(DqRule.class);
        rule.setName("dq executor test " + name + " " + UUID.randomUUID().toString().substring(0, 8));
        rule.setDataSource("main");
        rule.setTableName(TABLE);
        rule.setColumnName(columnName);
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(ruleType);
        rule.setRuleConfig(ruleConfig);
        rule.setSeverity(DqSeverity.HIGH);
        rule.setActive(true);
        rule.setDomain(domain);
        return dataManager.save(rule);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * Waits for the background run to leave {@link DqCheckRunStatus#RUNNING} and returns the run as
     * it was stored, which is the only way to observe an asynchronous run.
     */
    private DqCheckRun awaitFinished(DqCheckRun startedRun) {
        checkRuns.add(startedRun);
        assertEquals(DqCheckRunStatus.RUNNING, startedRun.getStatus(),
                "the run must be recorded as running before it is executed");

        long deadline = System.currentTimeMillis() + RUN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            DqCheckRun run = dataManager.load(DqCheckRun.class).id(startedRun.getId()).one();
            if (run.getStatus() != DqCheckRunStatus.RUNNING) {
                return run;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for check run " + startedRun.getId());
            }
        }
        return fail("check run " + startedRun.getId() + " did not finish within " + RUN_TIMEOUT_MS + " ms");
    }

    private List<DqCheckRunResult> loadResults(DqCheckRun run) {
        return dataManager.load(DqCheckRunResult.class)
                .query("select r from demo_DqCheckRunResult r where r.checkRun = :checkRun")
                .parameter("checkRun", run)
                // _base carries every local attribute; the references are only read for their id
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    private List<DqIssue> loadIssues(DqRule rule) {
        return dataManager.load(DqIssue.class)
                .query("select i from demo_DqIssue i where i.rule = :rule")
                .parameter("rule", rule)
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    private DqCheckRunResult resultOf(List<DqCheckRunResult> results, DqRule rule) {
        return results.stream()
                .filter(result -> rule.getId().equals(result.getRule().getId()))
                .findFirst()
                .orElseGet(() -> fail("no result stored for rule " + rule.getName()));
    }

    private JsonNode readSamples(DqCheckRunResult result) {
        assertNotNull(result.getSampleViolations(), "a failed rule must keep sample rows");
        try {
            return objectMapper.readTree(result.getSampleViolations());
        } catch (Exception e) {
            return fail("sample violations are not valid JSON: " + result.getSampleViolations(), e);
        }
    }

    @AfterEach
    void tearDown() {
        // issues first, then results, then the runs that own them: each step drops references the
        // next one would otherwise leave dangling
        for (DqRule rule : List.of(passingRule, failingRule, unsupportedRule)) {
            loadIssues(rule).forEach(dataManager::remove);
        }
        for (DqCheckRun run : checkRuns) {
            loadResults(run).forEach(dataManager::remove);
        }
        checkRuns.forEach(dataManager::remove);
        checkRuns.clear();

        dataManager.remove(passingRule, failingRule, unsupportedRule);
        dataManager.remove(domain);
    }
}
