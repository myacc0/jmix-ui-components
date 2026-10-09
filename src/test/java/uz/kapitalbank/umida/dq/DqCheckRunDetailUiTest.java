package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
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
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckRunDetailView;
import uz.kapitalbank.umida.view.dq.DqCheckRunViolationsView;
import uz.kapitalbank.umida.view.dq.DqViolationsFragment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The results of a check run are listed on the run's own page. The violating rows of a failed rule are
 * read with the query stored on its result and shown in a dialog, a page of
 * {@value DqViolationsFragment#PAGE_SIZE} rows at a time.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqCheckRunDetailUiTest {

    /** More violating rows than a page holds, so that the second page is reachable. */
    private static final int VIOLATING_ROWS = DqViolationsFragment.PAGE_SIZE + 5;

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private DqTestData testData;
    private DqRule rule;
    private DqCheckRun checkRun;
    private final List<DqCheckRunResult> results = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();
        rule = testData.createRule(domain, "detail ui", DqRuleType.NOT_NULL, "short_name", "{}");

        checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource("main");
        checkRun.setTriggered(DqCheckRunTrigger.SYSTEM);
        checkRun.setStartedAt(OffsetDateTime.now());
        checkRun.setFinishedAt(OffsetDateTime.now());
        checkRun.setStatus(DqCheckRunStatus.SUCCESS);
        checkRun.setRulesTotal(2);
        checkRun.setRulesPassed(1);
        checkRun.setRulesFailed(1);
        checkRun = dataManager.save(checkRun);

        results.add(dataManager.save(newResult(DqCheckResultStatus.PASSED)));
        results.add(dataManager.save(newResult(DqCheckResultStatus.FAILED)));
    }

    @Test
    void resultsAreListedWithTheirRuleCode() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();

        for (String columnKey : List.of("ruleCode", "status", "totalRecords", "failedRecords",
                "passRate", "executionMs", "errorMessage")) {
            assertNotNull(grid.getColumnByKey(columnKey), columnKey + " is a column of the results grid");
        }

        assertEquals(results.size(), grid.getGenericDataView().getItems().count(),
                "every result of the run is listed");

        for (DqCheckRunResult result : results) {
            // reading the rule through the loaded result proves the fetch plan brought the code
            assertEquals(rule.getCode(), gridItem(grid, result).getRule().getCode());
        }
    }

    @Test
    void violationsFollowTheStatusOfTheSelectedResult() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();
        DqCheckRunDetailView detailView = UiTestUtils.getCurrentView();

        JmixButton executedQueryButton = UiTestUtils.getComponent(detailView, "executedQueryButton");
        JmixButton violationsButton = UiTestUtils.getComponent(detailView, "violationsButton");

        assertFalse(executedQueryButton.isEnabled(), "nothing is selected yet");
        assertFalse(violationsButton.isEnabled(), "nothing is selected yet");

        for (DqCheckRunResult result : results) {
            grid.select(gridItem(grid, result));

            assertTrue(executedQueryButton.isEnabled(),
                    "the query is recorded for every result, whatever its status");
            assertEquals(result.getStatus() == DqCheckResultStatus.FAILED,
                    violationsButton.isEnabled(),
                    "only a failed result has violating rows to show, not " + result.getStatus());
        }
    }

    @Test
    void violationsOpenAsAPagedGridOfTheQueriedColumns() {
        DataGrid<DqCheckRunResult> grid = openResultsGrid();
        grid.select(gridItem(grid, failedResult()));

        JmixButton violationsButton = UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "violationsButton");
        violationsButton.click();

        DqCheckRunViolationsView violationsView = openedViolationsView();
        DqViolationsFragment violations = UiTestUtils.getComponent(violationsView, "violationsFragment");
        Grid<?> violationsGrid = (Grid<?>) UiComponentUtils.getComponent(violations, "violationsDataGrid");
        assertEquals(List.of("code", "short_name"), headers(violationsGrid),
                "the columns are the ones the violations query selects");
        assertEquals(DqViolationsFragment.PAGE_SIZE, violationsGrid.getGenericDataView().getItems().count(),
                "the first page is full");

        JmixButton previousPageButton = (JmixButton) UiComponentUtils.getComponent(violations, "previousPageButton");
        JmixButton nextPageButton = (JmixButton) UiComponentUtils.getComponent(violations, "nextPageButton");
        Span pageStatusLabel = (Span) UiComponentUtils.getComponent(violations, "pageStatusLabel");
        assertFalse(previousPageButton.isEnabled(), "there is nothing before the first page");
        assertTrue(nextPageButton.isEnabled(), "the rows do not fit on one page");
        assertTrue(pageStatusLabel.getText().contains(String.valueOf(VIOLATING_ROWS)),
                "the status names the total: " + pageStatusLabel.getText());

        nextPageButton.click();

        assertEquals(VIOLATING_ROWS - DqViolationsFragment.PAGE_SIZE,
                violationsGrid.getGenericDataView().getItems().count(), "the second page holds the rest");
        assertTrue(previousPageButton.isEnabled());
        assertFalse(nextPageButton.isEnabled(), "the second page is the last one");
    }

    private DqCheckRunResult failedResult() {
        return results.stream()
                .filter(result -> result.getStatus() == DqCheckResultStatus.FAILED)
                .findFirst()
                .orElseGet(() -> fail("the run has no failed result"));
    }

    /**
     * The dialog the action opened. A dialog view is not a navigation target, so it is looked up in
     * the UI it is attached to — which it only is once the client response is built, and no round
     * trip produces one here.
     */
    private DqCheckRunViolationsView openedViolationsView() {
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        List<DqCheckRunViolationsView> found = UI.getCurrent().getChildren()
                .flatMap(DqCheckRunDetailUiTest::selfAndDescendants)
                .filter(DqCheckRunViolationsView.class::isInstance)
                .map(DqCheckRunViolationsView.class::cast)
                .toList();
        assertEquals(1, found.size(), "the violations dialog is open");
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
        result.setFailedRecords(BigInteger.valueOf(status == DqCheckResultStatus.FAILED ? VIOLATING_ROWS : 0));
        result.setPassRate(new BigDecimal(status == DqCheckResultStatus.FAILED ? "45.00" : "100.00"));
        result.setExecutionMs(12L);
        result.setExecutedQuery("select count(*) from " + DqTestData.DOMAIN_TABLE);
        if (status == DqCheckResultStatus.FAILED) {
            // stands for the violating rows: as many as the test needs, whatever the table holds
            result.setViolationsQuery("SELECT 'code-' || g AS code, 'name-' || g AS short_name"
                    + " FROM generate_series(1, " + VIOLATING_ROWS + ") g ORDER BY g");
            result.setErrorMessage(VIOLATING_ROWS + " rows violate the rule");
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
        testData.cleanup();
    }
}
