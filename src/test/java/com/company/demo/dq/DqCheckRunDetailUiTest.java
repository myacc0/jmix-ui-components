package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.dq.DqCheckRun;
import com.company.demo.entity.dq.DqCheckRunResult;
import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.entity.dq.DqRule;
import com.company.demo.enums.dq.DqCheckResultStatus;
import com.company.demo.enums.dq.DqCheckRunStatus;
import com.company.demo.enums.dq.DqDimension;
import com.company.demo.enums.dq.DqRuleType;
import com.company.demo.enums.dq.DqSeverity;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqcheckrun.DqCheckRunDetailView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;
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
import java.util.stream.Stream;

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

    /** Two rows over two columns: enough to prove the grid follows the samples query, not the entity. */
    private static final String SAMPLE_VIOLATIONS = "[{\"id\": 1, \"name\": null}, {\"id\": 2, \"name\": \"row\"}]";

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

    @Test
    void sampleViolationsOpenAsAGridOfTheSampledColumns() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();
        grid.select(gridItem(grid, failedResult()));

        clickSampleViolations();

        Grid<?> samples = dialogContent(Grid.class);
        assertEquals(List.of("id", "name"), headers(samples),
                "the columns are the ones the rule's samples query selected");
        assertEquals(2, samples.getGenericDataView().getItems().count(), "every sampled row is listed");
    }

    @Test
    void aSampleThatIsNotARowSetFallsBackToTheJsonEditor() {
        DqCheckRunResult failed = failedResult();
        // valid JSON, but nothing a grid can be built from
        failed.setSampleViolations("\"5 rows violate the rule\"");
        results.set(results.indexOf(failed), dataManager.save(failed));

        DataGrid<DqCheckRunResult> grid = openResultsGrid();
        grid.select(gridItem(grid, failedResult()));

        clickSampleViolations();

        assertEquals(CodeEditorMode.JSON, dialogContent(CodeEditor.class).getMode());
    }

    private DqCheckRunResult failedResult() {
        return results.stream()
                .filter(result -> result.getStatus() == DqCheckResultStatus.FAILED)
                .findFirst()
                .orElseGet(() -> fail("the run has no failed result"));
    }

    private void clickSampleViolations() {
        JmixButton button = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "sampleViolationsButton");
        button.click();
        // an opened dialog only attaches itself to the UI while the client response is built,
        // which no round trip produces here
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
    }

    /**
     * The single component the action put into the dialog. The dialog is an overlay attached to the
     * UI rather than to the view, and looking inside it keeps the view's own grid out of the search.
     */
    private <T extends Component> T dialogContent(Class<T> type) {
        List<T> found = UI.getCurrent().getChildren()
                .flatMap(DqCheckRunDetailUiTest::selfAndDescendants)
                .filter(Dialog.class::isInstance)
                .flatMap(Component::getChildren)
                .flatMap(DqCheckRunDetailUiTest::selfAndDescendants)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();

        assertEquals(1, found.size(), "the dialog shows exactly one " + type.getSimpleName());
        return found.get(0);
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(DqCheckRunDetailUiTest::selfAndDescendants));
    }

    private List<String> headers(Grid<?> grid) {
        return grid.getColumns().stream()
                .map(Grid.Column::getHeaderText)
                .toList();
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
            result.setSampleViolations(SAMPLE_VIOLATIONS);
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
