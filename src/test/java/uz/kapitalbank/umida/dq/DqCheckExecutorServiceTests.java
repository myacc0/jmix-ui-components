package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.dto.dq.DqRuleFilter;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.*;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.service.dq.DqCheckExecutorService;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.service.dq.DqViolationsService;
import uz.kapitalbank.umida.service.dq.DqViolationsService.ViolationsPage;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises a real check run end to end against the main store: the rules are evaluated over
 * {@code umida_dict_data_domain}, the table behind {@link DictDataDomain}, so the SQL the builder
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

    private static final long RUN_TIMEOUT_MS = 60_000;

    @Autowired
    DqCheckExecutorService checkExecutorService;

    @Autowired
    DataManager dataManager;

    @Autowired
    Metadata metadata;

    @Autowired
    DqViolationsService violationsService;

    @Autowired
    DqIssueService issueService;

    @Autowired
    DqDataSourceProvider dataSourceProvider;

    private final List<DqCheckRun> checkRuns = new ArrayList<>();
    private DqTestData testData;

    private DictDataDomain domain;
    private DqRule passingRule;
    private DqRule failingRule;
    private DqRule unsupportedRule;
    /** Rules a single test case adds on top of the three above, whose issues must go before they do. */
    private final List<DqRule> extraRules = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        // "short_name" is a NOT NULL column, so no row can violate the rule
        passingRule = testData.createRule(domain, "passing", DqRuleType.NOT_NULL, "short_name", "{}");
        // the domain created above has no parent, so this rule always finds a violation
        failingRule = testData.createRule(domain, "failing", DqRuleType.NOT_NULL, "parent_id", "{}");
        // the query builder does not translate this type yet
        unsupportedRule = testData.createRule(domain, "unsupported", DqRuleType.CUSTOM_SQL, "short_name",
                "{\"sql\": \"select 1\"}");
    }

    @Test
    void runsEveryRuleAndClosesTheRunWithItsTotals() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));

        assertEquals(DqCheckRunStatus.SUCCESS, run.getStatus());
        assertNull(run.getErrorMessage());
        assertNotNull(run.getFinishedAt());
        assertEquals("main", run.getDataSource());
        assertEquals(DqCheckRunTrigger.MANUAL, run.getTriggered());

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

        DqCheckRunResult failed = resultOf(results, failingRule);
        assertEquals(DqCheckResultStatus.FAILED, failed.getStatus());
        assertTrue(failed.getFailedRecords().signum() > 0,
                "the domain created by the test has no parent, so the rule must find a violation");
        assertTrue(failed.getPassRate().compareTo(new BigDecimal("100.00")) < 0);

        assertNotNull(failed.getViolationsQuery(), "a failed rule must keep the query of its violating rows");
        assertTrue(failed.getViolationsQuery().startsWith("SELECT "), failed.getViolationsQuery());

        DqCheckRunResult skipped = resultOf(results, unsupportedRule);
        assertEquals(DqCheckResultStatus.SKIPPED, skipped.getStatus());
        assertNotNull(skipped.getErrorMessage());
        assertTrue(skipped.getErrorMessage().contains(DqRuleType.CUSTOM_SQL.getId()),
                "the reason must name the rule type that is not supported: " + skipped.getErrorMessage());
        assertNull(skipped.getPassRate());
        assertNull(skipped.getViolationsQuery(), "a rule that was not translated has no query to keep");
    }

    @Test
    void theViolatingRowsAreReadPageByPageWithTheStoredQuery() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));
        DqCheckRunResult failed = resultOf(loadResults(run), failingRule);

        long count = violationsService.countViolations(failed.getId());
        assertTrue(count > 0, "the domain created by the test has no parent, so there is a violating row");

        ViolationsPage firstRow = violationsService.loadViolations(failed.getId(), 0, 1);
        assertFalse(firstRow.columns().isEmpty(), "the columns of the violating rows are reported");
        assertEquals(1, firstRow.rows().size(), "a page holds no more rows than asked for");
        assertEquals(firstRow.columns().size(), firstRow.rows().get(0).size());

        ViolationsPage pastTheEnd = violationsService.loadViolations(failed.getId(), Math.toIntExact(count), 50);
        assertTrue(pastTheEnd.rows().isEmpty(), "there is nothing past the last violating row");
        assertEquals(firstRow.columns(), pastTheEnd.columns(), "an empty page still reports its columns");
    }

    @Test
    void opensAnIssueForAFailedRuleOnly() {
        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter()));

        assertTrue(loadIssues(passingRule).isEmpty());
        assertTrue(loadIssues(unsupportedRule).isEmpty(), "a skipped rule says nothing about the data");

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size());

        DqIssue issue = issues.get(0);
        assertEquals(DqIssueStatus.OPEN, issue.getStatus());
        assertTrue(issue.getCode().startsWith(DqIssueService.CODE_PREFIX), issue.getCode());
        assertNotNull(issue.getCreatedAt());
        assertNotNull(issue.getDescription());
        // HIGH severity: the rule got the due period of its severity on save
        assertEquals(LocalDate.now().plusDays(DqSeverity.HIGH.getDayCost()), issue.getDueDate());

        DqCheckRunResult failed = resultOf(loadResults(run), failingRule);
        assertEquals(failed.getId(), issue.getCheckResult().getId());
    }

    @Test
    void reusesTheOpenIssueOnTheNextRun() {
        awaitFinished(checkExecutorService.startCheckRun(filter()));
        DqIssue firstIssue = loadIssues(failingRule).get(0);

        DqCheckRun secondRun = awaitFinished(checkExecutorService.startCheckRun(filter()));

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size(), "the rule keeps failing, so the open issue must be reused");
        assertEquals(firstIssue.getId(), issues.get(0).getId());
        assertEquals(firstIssue.getCode(), issues.get(0).getCode());
        // the issue now points at the newest result
        assertEquals(resultOf(loadResults(secondRun), failingRule).getId(),
                issues.get(0).getCheckResult().getId());
        assertNotNull(issues.get(0).getUpdatedAt());
    }

    @Test
    void closesTheOpenIssueAsFixedOnceTheRulePasses() {
        awaitFinished(checkExecutorService.startCheckRun(filter()));
        assertEquals(DqIssueStatus.OPEN, loadIssues(failingRule).get(0).getStatus());

        // a zero threshold stands for the data being fixed: the same violations no longer fail the rule
        failingRule = updateRuleConfig(failingRule, "{\"threshold\": 0}");
        awaitFinished(checkExecutorService.startCheckRun(filter()));

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size());
        assertEquals(DqIssueStatus.CLOSED, issues.get(0).getStatus());
        assertEquals(DqIssueClosingReason.FIXED, issues.get(0).getClosingReason());
        assertNotNull(issues.get(0).getClosedAt());
    }

    @Test
    void aDismissedIssueSuppressesNewOnesUntilTheKeyFieldsChange() {
        awaitFinished(checkExecutorService.startCheckRun(filter()));
        DqIssue issue = loadIssues(failingRule).get(0);
        issue.setClosingReason(DqIssueClosingReason.WONTFIX);
        issueService.markClosed(issue);
        dataManager.save(issue);

        awaitFinished(checkExecutorService.startCheckRun(filter()));
        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size(), "the failure was dismissed, so it opens no new issue");
        assertEquals(DqIssueClosingReason.WONTFIX, issues.get(0).getClosingReason());

        // another config makes it another check, whose failures are worth an issue again
        failingRule = updateRuleConfig(failingRule, "{\"threshold\": 100}");
        awaitFinished(checkExecutorService.startCheckRun(filter()));
        assertEquals(1, loadIssues(failingRule).stream()
                .filter(i -> i.getStatus() == DqIssueStatus.OPEN)
                .count());
    }

    @Test
    void notBlankFlagsAWhitespaceOnlyValueButNotAFilledOne() {
        DictDataDomain blankDomain = testData.createDomain();
        blankDomain.setDescription("   ");
        blankDomain = dataManager.save(blankDomain);
        DictDataDomain controlCharsDomain = testData.createDomain();
        controlCharsDomain.setDescription(" \t\r\n ");
        controlCharsDomain = dataManager.save(controlCharsDomain);
        DictDataDomain filledDomain = testData.createDomain();
        filledDomain.setDescription("\tfilled\n");
        filledDomain = dataManager.save(filledDomain);

        DqRule notBlankRule = testData.createRule(blankDomain, "not blank", DqRuleType.NOT_BLANK, "description",
                "{\"samplesQueryColumns\": [\"id\"]}");
        extraRules.add(notBlankRule);

        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter(blankDomain)));
        DqCheckRunResult result = resultOf(loadResults(run), notBlankRule);
        assertEquals(DqCheckResultStatus.FAILED, result.getStatus(), result.getErrorMessage());

        long count = violationsService.countViolations(result.getId());
        List<String> violatingIds = violationsService.loadViolations(result.getId(), 0, Math.toIntExact(count))
                .rows().stream()
                .map(row -> String.valueOf(row.get(0)))
                .toList();
        assertTrue(violatingIds.contains(blankDomain.getId().toString()),
                "a description of only spaces is blank");
        assertTrue(violatingIds.contains(controlCharsDomain.getId().toString()),
                "a description of tabs and line breaks is blank");
        assertFalse(violatingIds.contains(filledDomain.getId().toString()),
                "a filled description is not blank");
    }

    @Test
    void referentialFlagsAValueMissingFromTheReferenceColumn() {
        // the domain table serves as its own reference: a description must name the code of a domain
        DictDataDomain referencedDomain = testData.createDomain();
        DictDataDomain knownDomain = testData.createDomain();
        knownDomain.setDescription(referencedDomain.getCode());
        knownDomain = dataManager.save(knownDomain);
        DictDataDomain unknownDomain = testData.createDomain();
        unknownDomain.setDescription("no such code " + UUID.randomUUID());
        unknownDomain = dataManager.save(unknownDomain);
        // createDomain leaves the description empty
        DictDataDomain emptyDomain = testData.createDomain();

        String reference = "\"refSchema\": \"public\", \"refTable\": \"umida_dict_data_domain\"," +
                " \"refColumn\": \"code\", \"samplesQueryColumns\": [\"id\"]";
        DqRule nullAllowedRule = testData.createRule(referencedDomain, "referential", DqRuleType.REFERENTIAL,
                "description", "{" + reference + "}", rule -> rule.setDbSchema("public"));
        extraRules.add(nullAllowedRule);
        DqRule nullRejectedRule = testData.createRule(referencedDomain, "referential not null",
                DqRuleType.REFERENTIAL, "description", "{" + reference + ", \"allowNull\": false}",
                rule -> rule.setDbSchema("public"));
        extraRules.add(nullRejectedRule);

        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(filter(referencedDomain)));
        List<DqCheckRunResult> results = loadResults(run);

        DqCheckRunResult nullAllowed = resultOf(results, nullAllowedRule);
        assertEquals(DqCheckResultStatus.FAILED, nullAllowed.getStatus(), nullAllowed.getErrorMessage());
        List<String> violatingIds = violatingIds(nullAllowed);
        assertTrue(violatingIds.contains(unknownDomain.getId().toString()), "an unknown code violates");
        assertFalse(violatingIds.contains(knownDomain.getId().toString()), "a known code does not violate");
        assertFalse(violatingIds.contains(emptyDomain.getId().toString()), "an empty value is allowed by default");

        DqCheckRunResult nullRejected = resultOf(results, nullRejectedRule);
        assertEquals(DqCheckResultStatus.FAILED, nullRejected.getStatus(), nullRejected.getErrorMessage());
        violatingIds = violatingIds(nullRejected);
        assertTrue(violatingIds.contains(unknownDomain.getId().toString()), "an unknown code violates");
        assertFalse(violatingIds.contains(knownDomain.getId().toString()), "a known code does not violate");
        assertTrue(violatingIds.contains(emptyDomain.getId().toString()), "an empty value violates when rejected");

        // joining the reference must not multiply the checked rows
        assertEquals(nullAllowed.getTotalRecords(), nullRejected.getTotalRecords());
        // counted in SQL: the soft-deleted rows DataManager hides are rows of the table too
        assertEquals(dataSourceProvider.getJdbcTemplate("main")
                        .queryForObject("SELECT COUNT(*) FROM umida_dict_data_domain", Long.class),
                nullAllowed.getTotalRecords().longValueExact());
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
        return filter(domain);
    }

    private DqRuleFilter filter(DictDataDomain ruleDomain) {
        DqRuleFilter filter = metadata.create(DqRuleFilter.class);
        filter.setDataSource("main");
        // the domain is created by this test, so only its own rules are selected
        filter.setDomain(ruleDomain);
        return filter;
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
                .query("select r from umida_DqCheckRunResult r where r.checkRun = :checkRun")
                .parameter("checkRun", run)
                // _base carries every local attribute; the references are only read for their id
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    private List<DqIssue> loadIssues(DqRule rule) {
        return dataManager.load(DqIssue.class)
                .query("select i from umida_DqIssue i where i.rule = :rule")
                .parameter("rule", rule)
                .fetchPlan(FetchPlan.BASE)
                .list();
    }

    /** The first sample column of every violating row of a result: the id, by the rule's projection. */
    private List<String> violatingIds(DqCheckRunResult result) {
        long count = violationsService.countViolations(result.getId());
        return violationsService.loadViolations(result.getId(), 0, Math.toIntExact(count))
                .rows().stream()
                .map(row -> String.valueOf(row.get(0)))
                .toList();
    }

    private DqRule updateRuleConfig(DqRule rule, String ruleConfig) {
        DqRule loaded = dataManager.load(DqRule.class).id(rule.getId()).one();
        loaded.setRuleConfig(ruleConfig);
        return dataManager.save(loaded);
    }

    private DqCheckRunResult resultOf(List<DqCheckRunResult> results, DqRule rule) {
        return results.stream()
                .filter(result -> rule.getId().equals(result.getRule().getId()))
                .findFirst()
                .orElseGet(() -> fail("no result stored for rule " + rule.getName()));
    }

    @AfterEach
    void tearDown() {
        // issues first, then results, then the runs that own them: each step drops references the
        // next one would otherwise leave dangling
        List<DqRule> rules = new ArrayList<>(List.of(passingRule, failingRule, unsupportedRule));
        rules.addAll(extraRules);
        for (DqRule rule : rules) {
            loadIssues(rule).forEach(dataManager::remove);
        }
        extraRules.clear();
        for (DqCheckRun run : checkRuns) {
            loadResults(run).forEach(dataManager::remove);
        }
        checkRuns.forEach(dataManager::remove);
        checkRuns.clear();

        testData.cleanup();
    }
}
