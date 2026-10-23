package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.dto.dq.DqReconciliation;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.*;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.service.dq.DqCheckExecutorService;
import uz.kapitalbank.umida.service.dq.DqCrossSourceReconciler;
import uz.kapitalbank.umida.service.dq.DqDataSourceProvider;
import uz.kapitalbank.umida.service.dq.DqExcelExport;
import uz.kapitalbank.umida.service.dq.DqExcelExportService;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.service.dq.DqViolationsService;
import uz.kapitalbank.umida.service.dq.DqViolationsService.ViolationsPage;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises a real check run end to end against the main store: the rules are evaluated over
 * {@code umida_dict_data_domain}, the table behind {@link DictDataDomain}, so the SQL the builder
 * produces is actually executed.
 * <p>
 * The rules are tied to a domain created by the test and the check that is run holds the rules of that
 * domain, so a run covers exactly the rules of the test case and nothing a developer left in the
 * database. Row counts
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
    DqViolationsService violationsService;

    @Autowired
    DqIssueService issueService;

    @Autowired
    DqDataSourceProvider dataSourceProvider;

    @Autowired
    DqCrossSourceReconciler crossSourceReconciler;

    @Autowired
    DqExcelExportService excelExportService;

    private final List<DqCheckRun> checkRuns = new ArrayList<>();
    private DqTestData testData;

    private DictDataDomain domain;
    private DqRule passingRule;
    private DqRule failingRule;
    private DqRule misconfiguredRule;
    /** The check {@link #check} saved last. */
    private DqCheck lastCheck;
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
        // a cross-source rule with nothing to compare cannot be evaluated
        misconfiguredRule = testData.createRule(domain, "misconfigured", DqRuleType.CROSS_SOURCE_AGGREGATED,
                "short_name", "{}");
    }

    @Test
    void runsEveryRuleAndClosesTheRunWithItsTotals() {
        DqCheckRun run = awaitFinished(startRun());

        assertEquals(DqCheckRunStatus.SUCCESS, run.getStatus());
        assertNull(run.getErrorMessage());
        assertNotNull(run.getFinishedAt());
        assertEquals(DqCheckRunTrigger.MANUAL, run.getTriggered());
        assertEquals(lastCheck.getId(), checkOf(run).getId(), "the run belongs to the check it ran");

        assertEquals(3, run.getRulesTotal());
        assertEquals(1, run.getRulesPassed());
        assertEquals(1, run.getRulesFailed());
        assertEquals(1, run.getRulesSkipped());
        // the skipped rule is left out of the score: 1 of the 2 evaluated rules passed
        assertEquals(0, new BigDecimal("50.00").compareTo(run.getDqScore()));
    }

    @Test
    void anInactiveRuleOfTheCheckIsLeftOutOfTheRun() {
        // the check holds it, see check(): a rule may turn inactive after it was added
        testData.createRule(domain, "inactive", DqRuleType.NOT_NULL, "parent_id", "{}",
                rule -> rule.setActive(false));

        DqCheckRun run = awaitFinished(startRun());

        // only the three active rules of the set-up
        assertEquals(3, run.getRulesTotal());
    }

    @Test
    void aDeletedRuleIsNoLongerPartOfTheCheck() {
        DqCheck check = check(domain);
        dataManager.remove(dataManager.load(DqRule.class).id(misconfiguredRule.getId()).one());

        DqCheckRun run = awaitFinished(checkExecutorService.startCheckRun(check));

        assertEquals(2, run.getRulesTotal());
        assertEquals(0, run.getRulesSkipped());
    }

    @Test
    void eachResultRecordsTheDataSourceOfItsRule() {
        DqCheckRun run = awaitFinished(startRun());

        List<DqCheckRunResult> results = loadResults(run);
        assertEquals(3, results.size());
        results.forEach(result -> assertEquals("main", result.getDataSource()));
    }

    @Test
    void storesAResultPerRule() {
        DqCheckRun run = awaitFinished(startRun());
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

        DqCheckRunResult skipped = resultOf(results, misconfiguredRule);
        assertEquals(DqCheckResultStatus.SKIPPED, skipped.getStatus());
        assertNotNull(skipped.getErrorMessage());
        assertTrue(skipped.getErrorMessage().contains("second data source"),
                "the reason must name what the configuration lacks: " + skipped.getErrorMessage());
        assertNull(skipped.getPassRate());
        assertNull(skipped.getViolationsQuery(), "a rule that was not evaluated has no query to keep");
        assertNull(skipped.getReconciliation(), "a rule that was not evaluated compared nothing");
    }

    @Test
    void theViolatingRowsAreReadPageByPageWithTheStoredQuery() {
        DqCheckRun run = awaitFinished(startRun());
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
    void everyViolatingRowIsExportedToExcelWithTheColumnsOfTheQuery() {
        DqCheckRun run = awaitFinished(startRun());
        DqCheckRunResult failed = resultOf(loadResults(run), failingRule);
        long count = violationsService.countViolations(failed.getId());
        ViolationsPage page = violationsService.loadViolations(failed.getId(), 0, Math.toIntExact(count));

        DqExcelExport export = excelExportService.exportViolations(failed.getId());

        assertEquals(count, export.rows());
        assertFalse(export.truncated());
        assertTrue(export.fileName().startsWith(failingRule.getCode() + "-violations-"), export.fileName());
        assertTrue(export.fileName().endsWith(".xlsx"), export.fileName());
        Sheet sheet = readSheet(export);
        assertEquals(page.columns(), rowTexts(sheet.getRow(0)), "the header row holds the columns of the query");
        assertEquals(count, sheet.getLastRowNum(), "a row per violating row below the header");
        assertEquals(String.valueOf(page.rows().get(0).get(0)), rowTexts(sheet.getRow(1)).get(0),
                "the rows are written in the order of the query");
        assertFalse(Files.exists(export.file()), "the workbook is removed once it has been read");
    }

    @Test
    void theViolatingRowsAreReadNoFurtherThanAsked() {
        DqCheckRun run = awaitFinished(startRun());
        DqCheckRunResult failed = resultOf(loadResults(run), failingRule);
        List<List<String>> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        long read = violationsService.readViolations(failed.getId(), 1, new DqViolationsService.ViolationsHandler() {
            @Override
            public void columns(List<String> labels) {
                columns.add(labels);
            }

            @Override
            public void row(List<Object> values) {
                rows.add(values);
            }
        });

        assertEquals(1, read);
        assertEquals(1, rows.size());
        assertEquals(List.of(violationsService.loadViolations(failed.getId(), 0, 1).columns()), columns,
                "the columns are reported once, before the rows");
    }

    @Test
    void opensAnIssueForAFailedRuleOnly() {
        DqCheckRun run = awaitFinished(startRun());

        assertTrue(loadIssues(passingRule).isEmpty());
        assertTrue(loadIssues(misconfiguredRule).isEmpty(), "a skipped rule says nothing about the data");

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
        awaitFinished(startRun());
        DqIssue firstIssue = loadIssues(failingRule).get(0);

        DqCheckRun secondRun = awaitFinished(startRun());

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
        awaitFinished(startRun());
        assertEquals(DqIssueStatus.OPEN, loadIssues(failingRule).get(0).getStatus());

        // a zero threshold stands for the data being fixed: the same violations no longer fail the rule
        failingRule = updateRuleConfig(failingRule, "{\"threshold\": 0}");
        awaitFinished(startRun());

        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size());
        assertEquals(DqIssueStatus.CLOSED, issues.get(0).getStatus());
        assertEquals(DqIssueClosingReason.FIXED, issues.get(0).getClosingReason());
        assertNotNull(issues.get(0).getClosedAt());
    }

    @Test
    void aDismissedIssueSuppressesNewOnesUntilTheKeyFieldsChange() {
        awaitFinished(startRun());
        DqIssue issue = loadIssues(failingRule).get(0);
        issue.setClosingReason(DqIssueClosingReason.WONTFIX);
        issueService.markClosed(issue);
        dataManager.save(issue);

        awaitFinished(startRun());
        List<DqIssue> issues = loadIssues(failingRule);
        assertEquals(1, issues.size(), "the failure was dismissed, so it opens no new issue");
        assertEquals(DqIssueClosingReason.WONTFIX, issues.get(0).getClosingReason());

        // another config makes it another check, whose failures are worth an issue again
        failingRule = updateRuleConfig(failingRule, "{\"threshold\": 100}");
        awaitFinished(startRun());
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

        DqCheckRun run = awaitFinished(startRun(blankDomain));
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

        DqCheckRun run = awaitFinished(startRun(referencedDomain));
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
    void aCustomSqlRuleTakesBothCountsFromItsQuery() {
        DqRule failing = customSqlRule("custom failing", "select 10, 4;", "select id from umida_dict_data_domain;");
        DqRule passing = customSqlRule("custom passing",
                "select count(*), count(*) filter (where short_name is null) from umida_dict_data_domain",
                "select id from umida_dict_data_domain where short_name is null");

        List<DqCheckRunResult> results = loadResults(awaitFinished(startRun()));

        DqCheckRunResult failed = resultOf(results, failing);
        assertEquals(DqCheckResultStatus.FAILED, failed.getStatus(), failed.getErrorMessage());
        assertEquals(BigInteger.TEN, failed.getTotalRecords());
        assertEquals(BigInteger.valueOf(4), failed.getFailedRecords());
        assertEquals(0, new BigDecimal("60.00").compareTo(failed.getPassRate()));
        assertEquals("select 10, 4", failed.getExecutedQuery());
        assertEquals("select id from umida_dict_data_domain", failed.getViolationsQuery());
        List<DqIssue> issues = loadIssues(failing);
        assertEquals(1, issues.size(), "a failed custom rule opens an issue like any other");
        assertTrue(issues.get(0).getDescription().endsWith("4 of 10.") || issues.get(0).getDescription().endsWith("4 из 10."),
                "a custom rule names no table: " + issues.get(0).getDescription());

        DqCheckRunResult passed = resultOf(results, passing);
        assertEquals(DqCheckResultStatus.PASSED, passed.getStatus(), passed.getErrorMessage());
        assertEquals(BigInteger.ZERO, passed.getFailedRecords());
    }

    @Test
    void aCustomSqlRuleCannotChangeTheData() {
        // passes the text-level checks, but the sequence would advance: a write the database refuses
        DqRule writing = customSqlRule("custom writing", "select nextval('umida_dq_rule_code_seq'), 0",
                "select 1");

        List<DqCheckRunResult> results = loadResults(awaitFinished(startRun()));

        DqCheckRunResult skipped = resultOf(results, writing);
        assertEquals(DqCheckResultStatus.SKIPPED, skipped.getStatus());
        assertTrue(skipped.getErrorMessage().contains("read-only"), skipped.getErrorMessage());
    }

    @Test
    void theViolationsOfACustomSqlRuleArePagedFromItsOwnQuery() {
        // a CTE, a LIMIT of its own and a trailing comment: none of them may break the paging
        DqRule rule = customSqlRule("custom violations", "select 1, 1",
                "with d as (select id, short_name from umida_dict_data_domain where id = '" + domain.getId() + "')"
                        + " select * from d order by id limit 5 -- the domain of this test");

        DqCheckRunResult result = resultOf(loadResults(awaitFinished(startRun())),
                rule);

        assertEquals(DqCheckResultStatus.FAILED, result.getStatus(), result.getErrorMessage());
        assertEquals(1, violationsService.countViolations(result.getId()));
        DqViolationsService.ViolationsPage page = violationsService.loadViolations(result.getId(), 0, 10);
        assertEquals(List.of("id", "short_name"), page.columns());
        assertEquals(List.of(domain.getId().toString()), violatingIds(result));
        assertTrue(violationsService.loadViolations(result.getId(), 1, 10).rows().isEmpty(),
                "the second page starts past the only row");

        Sheet sheet = readSheet(excelExportService.exportViolations(result.getId()));
        assertEquals(List.of("id", "short_name"), rowTexts(sheet.getRow(0)));
        assertEquals(1, sheet.getLastRowNum(), "the export is read from the rule's own query, like a page");
        assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(0).getCellType(), "a numeric id stays a number");
        assertEquals(domain.getId().toString(), rowTexts(sheet.getRow(1)).get(0));
    }

    @Test
    void aCrossSourceRuleFailsWhenAnyPairDiffers() {
        // both "sources" are the main store: what is compared is the rows, not the databases
        DqRule rule = crossSourceRule("cross failing",
                "select 10 as total, 2.50 as amount, null::numeric as missing, 'x' as note",
                "select 10.00 as cnt, 3 as sum_amount, null::numeric as nothing",
                "[{\"primaryAlias\": \"total\", \"secondaryAlias\": \"cnt\"},"
                        + " {\"primaryAlias\": \"AMOUNT\", \"secondaryAlias\": \"sum_amount\"},"
                        + " {\"primaryAlias\": \"missing\", \"secondaryAlias\": \"nothing\"}]");

        DqCheckRunResult result = resultOf(loadResults(awaitFinished(startRun())),
                rule);

        assertEquals(DqCheckResultStatus.FAILED, result.getStatus(), result.getErrorMessage());
        assertEquals(BigInteger.valueOf(3), result.getTotalRecords(), "every pair is a record");
        assertEquals(BigInteger.ONE, result.getFailedRecords(), "only the amounts differ");
        assertNull(result.getViolationsQuery(), "the compared rows are in two databases: there is no query to read them");
        assertTrue(result.getExecutedQuery().startsWith("-- main\nselect 10 as total"), result.getExecutedQuery());

        DqReconciliation reconciliation = crossSourceReconciler.fromJson(result.getReconciliation());
        assertNotNull(reconciliation, "what was compared is kept: " + result.getReconciliation());
        assertEquals("main", reconciliation.getPrimary().getDataSource());
        assertEquals("x", reconciliation.getPrimary().getRow().get("note"), "the whole row is kept");
        List<DqReconciliation.Pair> pairs = reconciliation.getPairs();
        assertEquals(3, pairs.size());
        assertTrue(pairs.get(0).isMatched(), "10 equals 10.00");
        assertFalse(pairs.get(1).isMatched());
        assertEquals(0, new BigDecimal("2.50").compareTo(pairs.get(1).getPrimaryValue()),
                "an alias is found whatever its case");
        assertEquals(0, new BigDecimal("-0.50").compareTo(pairs.get(1).difference()));
        assertTrue(pairs.get(2).isMatched(), "two NULLs match");

        List<DqIssue> issues = loadIssues(rule);
        assertEquals(1, issues.size(), "a failed cross-source rule opens an issue like any other");
        assertTrue(issues.get(0).getDescription().contains("AMOUNT / sum_amount"),
                "the issue names the pairs that differ: " + issues.get(0).getDescription());

        DqExcelExport export = excelExportService.exportReconciliation(result.getId());
        assertEquals(3, export.rows());
        assertTrue(export.fileName().startsWith(rule.getCode() + "-reconciliation-"), export.fileName());
        Sheet sheet = readSheet(export);
        assertEquals(6, sheet.getRow(0).getLastCellNum(), "the columns of the reconciliation on screen");
        assertEquals(3, sheet.getLastRowNum(), "a row per pair below the header");
        Row amounts = sheet.getRow(2);
        assertEquals("AMOUNT", amounts.getCell(0).getStringCellValue());
        assertEquals(CellType.NUMERIC, amounts.getCell(1).getCellType(), "a value stays a number");
        assertEquals(2.5, amounts.getCell(1).getNumericCellValue());
        assertEquals(-0.5, amounts.getCell(4).getNumericCellValue(), "the difference is written too");
        assertEquals(CellType.BLANK, sheet.getRow(3).getCell(1).getCellType(), "a NULL stays empty");
    }

    @Test
    void aCrossSourceRulePassesWhenEveryPairMatches() {
        DqRule rule = crossSourceRule("cross passing",
                "select count(*) as cnt from umida_dict_data_domain",
                "select count(*) as total from umida_dict_data_domain",
                "[{\"primaryAlias\": \"cnt\", \"secondaryAlias\": \"total\"}]");

        DqCheckRunResult result = resultOf(loadResults(awaitFinished(startRun())),
                rule);

        assertEquals(DqCheckResultStatus.PASSED, result.getStatus(), result.getErrorMessage());
        assertEquals(BigInteger.ZERO, result.getFailedRecords());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getPassRate()));
        assertNotNull(result.getReconciliation(), "a passed check keeps what it compared too");
        assertTrue(loadIssues(rule).isEmpty());
    }

    @Test
    void aCrossSourceQueryMustReturnExactlyOneRow() {
        DqRule noRows = crossSourceRule("cross no rows", "select 1 as a where false", "select 1 as b",
                "[{\"primaryAlias\": \"a\", \"secondaryAlias\": \"b\"}]");
        DqRule twoRows = crossSourceRule("cross two rows", "select 1 as a",
                "select g as b from generate_series(1, 2) g",
                "[{\"primaryAlias\": \"a\", \"secondaryAlias\": \"b\"}]");
        DqRule unknownAlias = crossSourceRule("cross unknown alias", "select 1 as a", "select 1 as b",
                "[{\"primaryAlias\": \"a\", \"secondaryAlias\": \"c\"}]");

        List<DqCheckRunResult> results = loadResults(awaitFinished(startRun()));

        for (DqRule rule : List.of(noRows, twoRows, unknownAlias)) {
            DqCheckRunResult result = resultOf(results, rule);
            assertEquals(DqCheckResultStatus.SKIPPED, result.getStatus(), rule.getName());
            assertTrue(loadIssues(rule).isEmpty(), "a rule that could not be evaluated opens no issue");
        }
        assertTrue(resultOf(results, noRows).getErrorMessage().contains("returned none"),
                resultOf(results, noRows).getErrorMessage());
        assertTrue(resultOf(results, twoRows).getErrorMessage().contains("returned more"),
                resultOf(results, twoRows).getErrorMessage());
        assertTrue(resultOf(results, unknownAlias).getErrorMessage().contains("'c'"),
                resultOf(results, unknownAlias).getErrorMessage());
    }

    @Test
    void rejectsACheckWithoutActiveRules() {
        DictDataDomain inactiveDomain = testData.createDomain();
        extraRules.add(testData.createRule(inactiveDomain, "inactive only", DqRuleType.NOT_NULL, "parent_id", "{}",
                rule -> rule.setActive(false)));
        DqCheck check = check(inactiveDomain);

        assertThrows(IllegalArgumentException.class, () -> checkExecutorService.startCheckRun(check));
    }

    // ---------------------------------------------------------------------
    // test data
    // ---------------------------------------------------------------------

    /** Runs a check of the rules of the test domain. */
    private DqCheckRun startRun() {
        return startRun(domain);
    }

    private DqCheckRun startRun(DictDataDomain ruleDomain) {
        return checkExecutorService.startCheckRun(check(ruleDomain));
    }

    /**
     * A check holding every rule of the domain, inactive ones included. The domain is created by this
     * test, so the check holds only its own rules.
     */
    private DqCheck check(DictDataDomain ruleDomain) {
        List<DqRule> rules = dataManager.load(DqRule.class)
                .query("select r from umida_DqRule r where r.domain = :domain")
                .parameter("domain", ruleDomain)
                .list();
        DqCheck check = dataManager.create(DqCheck.class);
        check.setName("dq test check " + UUID.randomUUID());
        check.setRules(new HashSet<>(rules));
        // tracked after the rules, so that cleanup removes the check first
        lastCheck = testData.track(dataManager.save(check));
        return lastCheck;
    }

    private DqCheck checkOf(DqCheckRun run) {
        return dataManager.load(DqCheckRun.class)
                .id(run.getId())
                .fetchPlan(fp -> fp.add("check"))
                .one()
                .getCheck();
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

    /**
     * The only sheet of an exported workbook, read through the stream a download reads it with. The
     * workbook is read into memory, so it is left open for the sheet to be read.
     */
    private static Sheet readSheet(DqExcelExport export) {
        XSSFWorkbook workbook;
        try (InputStream in = export.openStream()) {
            workbook = new XSSFWorkbook(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(1, workbook.getNumberOfSheets());
        return workbook.getSheetAt(0);
    }

    /** The values of a row as text; a numeric cell is read as the number it holds. */
    private static List<String> rowTexts(Row row) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            texts.add(cell == null ? "" : switch (cell.getCellType()) {
                case NUMERIC -> new BigDecimal(String.valueOf(cell.getNumericCellValue()))
                        .stripTrailingZeros().toPlainString();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case BLANK -> "";
                default -> cell.getStringCellValue();
            });
        }
        return texts;
    }

    private DqRule customSqlRule(String name, String sql, String violationsSql) {
        DqRule rule = testData.createRule(domain, name, DqRuleType.CUSTOM_SQL, null,
                "{\"sql\": \"" + sql + "\", \"violationsSql\": \"" + violationsSql + "\"}",
                custom -> custom.setTableName(null));
        extraRules.add(rule);
        return rule;
    }

    /** A cross-source rule comparing two queries of the main store. */
    private DqRule crossSourceRule(String name, String primarySql, String secondarySql, String pairsJson) {
        DqRule rule = testData.createRule(domain, name, DqRuleType.CROSS_SOURCE_AGGREGATED, null,
                "{\"crossPrimarySql\": \"" + primarySql + "\", \"crossDataSource\": \"main\","
                        + " \"crossDataSourceSql\": \"" + secondarySql + "\", \"crossColumnPairs\": " + pairsJson + "}",
                custom -> custom.setTableName(null));
        extraRules.add(rule);
        return rule;
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
        List<DqRule> rules = new ArrayList<>(List.of(passingRule, failingRule, misconfiguredRule));
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
