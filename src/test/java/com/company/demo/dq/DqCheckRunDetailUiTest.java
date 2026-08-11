package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.DqCheckRun;
import com.company.demo.entity.DqCheckRunResult;
import com.company.demo.entity.DqDataDomain;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqCheckResultStatus;
import com.company.demo.enums.DqCheckRunStatus;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqcheckrun.DqCheckRunDetailView;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The results of a check run are listed on the run's own page. Sample violations are only collected
 * for a rule that failed, so that button follows the status of the selected row.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunDetailUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private DqDataDomain domain;
    private DqRule rule;
    private DqCheckRun checkRun;
    private final List<DqCheckRunResult> results = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        domain = dataManager.create(DqDataDomain.class);
        domain.setCode("dq-run-detail-" + suffix);
        domain.setShortName("DQ RUN " + suffix);
        domain.setName("DQ check run detail UI test domain " + suffix);
        domain.setCreatedAt(LocalDateTime.now());
        domain = dataManager.save(domain);

        rule = dataManager.create(DqRule.class);
        rule.setName("dq check run detail ui test rule " + suffix);
        rule.setDataSource("main");
        rule.setTableName("demo_dq_data_domain");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{}");
        rule.setSeverity(DqSeverity.CRITICAL);
        rule.setActive(true);
        rule.setDomain(domain);
        rule = dataManager.save(rule);

        checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource("main");
        checkRun.setTriggeredUsername("admin");
        checkRun.setStartedAt(LocalDateTime.now());
        checkRun.setFinishedAt(LocalDateTime.now());
        checkRun.setStatus(DqCheckRunStatus.SUCCESS);
        checkRun.setRulesTotal(2);
        checkRun.setRulesPassed(1);
        checkRun.setRulesFailed(1);
        checkRun = dataManager.save(checkRun);

        results.add(dataManager.save(newResult(DqCheckResultStatus.PASSED)));
        results.add(dataManager.save(newResult(DqCheckResultStatus.FAILED)));
    }

    @Test
    void resultsAreListedWithTheirRuleName() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();

        for (String columnKey : List.of("ruleName", "status", "totalRecords", "failedRecords",
                "passRate", "executionMs", "errorMessage")) {
            assertNotNull(grid.getColumnByKey(columnKey), columnKey + " is a column of the results grid");
        }

        assertEquals(results.size(), grid.getGenericDataView().getItems().count(),
                "every result of the run is listed");

        for (DqCheckRunResult result : results) {
            // reading the rule through the loaded result proves the fetch plan brought the reference
            assertEquals(rule.getName(), gridItem(grid, result).getRule().getName());
        }
    }

    @Test
    void sampleViolationsFollowTheStatusOfTheSelectedResult() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();
        DqCheckRunDetailView detailView = UiTestUtils.getCurrentView();

        JmixButton executedQueryButton = UiTestUtils.getComponent(detailView, "executedQueryButton");
        JmixButton sampleViolationsButton = UiTestUtils.getComponent(detailView, "sampleViolationsButton");

        assertFalse(executedQueryButton.isEnabled(), "nothing is selected yet");
        assertFalse(sampleViolationsButton.isEnabled(), "nothing is selected yet");

        for (DqCheckRunResult result : results) {
            grid.select(gridItem(grid, result));

            assertTrue(executedQueryButton.isEnabled(),
                    "the query is recorded for every result, whatever its status");
            assertEquals(result.getStatus() == DqCheckResultStatus.FAILED,
                    sampleViolationsButton.isEnabled(),
                    "only a failed result has sampled violations to show, not " + result.getStatus());
        }
    }

    private DqCheckRunResult newResult(DqCheckResultStatus status) {
        DqCheckRunResult result = dataManager.create(DqCheckRunResult.class);
        result.setCheckRun(checkRun);
        result.setRule(rule);
        result.setStatus(status);
        result.setTotalRecords(BigInteger.valueOf(100));
        result.setFailedRecords(BigInteger.valueOf(status == DqCheckResultStatus.FAILED ? 5 : 0));
        result.setPassRate(new BigDecimal(status == DqCheckResultStatus.FAILED ? "95.00" : "100.00"));
        result.setExecutionMs(12L);
        result.setExecutedQuery("select count(*) from demo_dq_data_domain");
        if (status == DqCheckResultStatus.FAILED) {
            result.setSampleViolations("[{\"name\": null}]");
            result.setErrorMessage("5 rows violate the rule");
        }
        return result;
    }

    private DataGrid<DqCheckRunResult> openResultsGrid() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqCheckRun.class)
                .editEntity(checkRun)
                .withViewClass(DqCheckRunDetailView.class)
                .navigate();
        DqCheckRunDetailView detailView = UiTestUtils.getCurrentView();
        return UiTestUtils.getComponent(detailView, "checkResultsDataGrid");
    }

    /** The grid's own instance of the given result — the one its selection model can match. */
    private DqCheckRunResult gridItem(DataGrid<DqCheckRunResult> grid, DqCheckRunResult expected) {
        return grid.getGenericDataView().getItems()
                .filter(item -> expected.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> fail("result " + expected.getId() + " is not shown on the run page"));
    }

    @AfterEach
    void tearDown() {
        results.forEach(dataManager::remove);
        results.clear();
        dataManager.remove(checkRun);
        dataManager.remove(rule);
        dataManager.remove(domain);
    }
}
