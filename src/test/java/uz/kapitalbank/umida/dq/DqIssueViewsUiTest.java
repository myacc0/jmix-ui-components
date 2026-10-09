package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.valuepicker.JmixValuePicker;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.testassist.dialog.DialogInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.UmidaApplication;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.dq.DqRuleGroup;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckRunViolationsView;
import uz.kapitalbank.umida.view.dq.DqIssueDetailView;
import uz.kapitalbank.umida.view.dq.DqIssueListView;
import uz.kapitalbank.umida.view.dq.DqRuleDetailView;
import uz.kapitalbank.umida.view.dq.DqViolationsFragment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue screens show what an issue takes from its rule and its latest failed check, offer its
 * violating rows, and offer closing only to the assignee subdivision — which {@code admin}, having no
 * employee, is not part of. The rule editor asks before moving a rule with open issues.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueViewsUiTest {

    private static final int VIOLATING_ROWS = 7;

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    CurrentAuthentication currentAuthentication;

    private DqTestData testData;
    private DqRule rule;
    private DqCheckRunResult result;
    private DqIssue issue;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();

        OrgStructureSubdivision assignee = dataManager.create(OrgStructureSubdivision.class);
        assignee.setId(UUID.randomUUID().toString());
        assignee.setName("DQ issue views " + UUID.randomUUID().toString().substring(0, 8));
        OrgStructureSubdivision savedAssignee = testData.track(dataManager.save(assignee));

        // the editor asks for a group, which the rule of a check run need not have
        DqRuleGroup group = dataManager.create(DqRuleGroup.class);
        group.setName("DQ issue views " + UUID.randomUUID().toString().substring(0, 8));
        DqRuleGroup savedGroup = testData.track(dataManager.save(group));

        rule = testData.createRule(domain, "issue views", DqRuleType.NOT_NULL, "short_name", "{}", r -> {
            r.setGroup(savedGroup);
            r.setDbSchema("public");
            r.setSeverity(DqSeverity.CRITICAL);
            r.setAssignee(savedAssignee);
        });

        DqCheckRun run = dataManager.create(DqCheckRun.class);
        run.setDataSource("main");
        run.setTriggered(DqCheckRunTrigger.SYSTEM);
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(DqCheckRunStatus.SUCCESS);
        run.setRulesTotal(1);
        run = testData.track(dataManager.save(run));

        DqCheckRunResult failed = dataManager.create(DqCheckRunResult.class);
        failed.setCheckRun(run);
        failed.setRule(rule);
        failed.setStatus(DqCheckResultStatus.FAILED);
        failed.setTotalRecords(BigInteger.valueOf(100));
        failed.setFailedRecords(BigInteger.valueOf(VIOLATING_ROWS));
        failed.setPassRate(new BigDecimal("93.00"));
        // stands for the violating rows: as many as the test needs, whatever the table holds
        failed.setViolationsQuery("SELECT 'code-' || g AS code FROM generate_series(1, " + VIOLATING_ROWS + ") g");
        result = testData.track(dataManager.save(failed));

        DqIssue newIssue = dataManager.create(DqIssue.class);
        newIssue.setRule(rule);
        newIssue.setCheckResult(result);
        newIssue.setStatus(DqIssueStatus.OPEN);
        issue = testData.track(dataManager.save(newIssue));
    }

    @Test
    void theListShowsWhatTheIssueTakesFromItsRuleAndCheck() {
        DataGrid<DqIssue> grid = openList();

        for (String key : new String[]{"code", "ruleCode", "dataSource", "severity", "affectedRows", "status"}) {
            assertNotNull(grid.getColumnByKey(key), key + " is a column of the issue list");
        }
        DqIssue shown = gridItem(grid);
        assertTrue(shown.getCode().startsWith("DQI-"));
        assertEquals(rule.getCode(), shown.getRule().getCode());
        assertEquals("main", shown.getRule().getDataSource());
        assertEquals(DqSeverity.CRITICAL, shown.getRule().getSeverity());
        assertEquals(BigInteger.valueOf(VIOLATING_ROWS), shown.getCheckResult().getFailedRecords());

        DqIssueListView view = UiTestUtils.getCurrentView();
        assertThrows(IllegalArgumentException.class, () -> UiTestUtils.getComponent(view, "setAssigneeButton"),
                "the issue is assigned through its rule, not by hand");
    }

    @Test
    void theListOffersTheViolationsButClosingOnlyToTheAssignee() {
        DataGrid<DqIssue> grid = openList();
        DqIssueListView view = UiTestUtils.getCurrentView();
        JmixButton closeIssueButton = UiTestUtils.getComponent(view, "closeIssueButton");
        JmixButton violationsButton = UiTestUtils.getComponent(view, "violationsButton");

        grid.select(gridItem(grid));

        assertFalse(closeIssueButton.isEnabled(), "admin is not an employee of the assignee subdivision");
        assertTrue(violationsButton.isEnabled(), "the latest check of the issue failed with a query");

        violationsButton.click();
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        DqCheckRunViolationsView violationsView = UI.getCurrent().getChildren()
                .flatMap(DqIssueViewsUiTest::selfAndDescendants)
                .filter(DqCheckRunViolationsView.class::isInstance)
                .map(DqCheckRunViolationsView.class::cast)
                .findFirst()
                .orElseGet(() -> fail("the violations dialog is open"));
        DqViolationsFragment fragment = UiTestUtils.getComponent(violationsView, "violationsFragment");
        Grid<?> violationsGrid = (Grid<?>) UiComponentUtils.getComponent(fragment, "violationsDataGrid");
        assertEquals(VIOLATING_ROWS, violationsGrid.getGenericDataView().getItems().count());
    }

    @Test
    void theDetailShowsTheIssueAndItsViolatingRows() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqIssue.class)
                .editEntity(issue)
                .withViewClass(DqIssueDetailView.class)
                .navigate();
        DqIssueDetailView view = UiTestUtils.getCurrentView();

        TypedTextField<?> ruleField = UiTestUtils.getComponent(view, "ruleField");
        TypedTextField<?> dataSourceField = UiTestUtils.getComponent(view, "dataSourceField");
        TypedTextField<?> affectedRowsField = UiTestUtils.getComponent(view, "affectedRowsField");
        assertEquals(rule.getCode(), ruleField.getTypedValue());
        assertEquals("main", dataSourceField.getTypedValue());
        assertEquals(BigInteger.valueOf(VIOLATING_ROWS), affectedRowsField.getTypedValue());
        assertTrue(ruleField.isReadOnly());

        JmixButton closeIssueButton = UiTestUtils.getComponent(view, "closeIssueButton");
        assertFalse(closeIssueButton.isEnabled(), "admin is not an employee of the assignee subdivision");

        DqViolationsFragment fragment = UiTestUtils.getComponent(view, "violationsFragment");
        Grid<?> violationsGrid = (Grid<?>) UiComponentUtils.getComponent(fragment, "violationsDataGrid");
        Span pageStatusLabel = (Span) UiComponentUtils.getComponent(fragment, "pageStatusLabel");
        assertEquals(VIOLATING_ROWS, violationsGrid.getGenericDataView().getItems().count());
        assertTrue(pageStatusLabel.getText().contains(String.valueOf(VIOLATING_ROWS)), pageStatusLabel.getText());
    }

    @Test
    void theDetailShowsTheDatesInTheUserTimeZoneWithoutTheOffset() {
        // the creation time cannot be changed later, so a new issue is created at a known instant
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 3, 5, 23, 30, 15, 0, ZoneOffset.UTC);
        DqIssue newIssue = dataManager.create(DqIssue.class);
        newIssue.setRule(rule);
        newIssue.setCheckResult(result);
        newIssue.setStatus(DqIssueStatus.RESOLVED);
        newIssue.setCreatedAt(createdAt);
        DqIssue datedIssue = testData.track(dataManager.save(newIssue));

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqIssue.class)
                .editEntity(datedIssue)
                .withViewClass(DqIssueDetailView.class)
                .navigate();
        DqIssueDetailView view = UiTestUtils.getCurrentView();

        JmixValuePicker<?> createdAtField = UiTestUtils.getComponent(view, "createdAtField");
        String expected = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .format(createdAt.atZoneSameInstant(currentAuthentication.getTimeZone().toZoneId()));
        assertEquals(expected, createdAtField.getElement().getProperty("value"));
        assertTrue(createdAtField.isReadOnly());
    }

    @Test
    void movingARuleWithOpenIssuesAsksFirstAndClosesThem() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqRule.class)
                .editEntity(rule)
                .withViewClass(DqRuleDetailView.class)
                .navigate();
        DqRuleDetailView view = UiTestUtils.getCurrentView();
        JmixComboBox<String> columnNameField = UiTestUtils.getComponent(view, "columnNameField");
        columnNameField.setValue("code");

        assertTrue(UiTestUtils.validateView(view).isEmpty(), () -> UiTestUtils.validateView(view).getAll().stream().map(e -> e.getDescription() + " @ " + e.getComponent()).toList().toString());
        view.save();

        DialogInfo dialog = UiTestUtils.getLastOpenedDialog();
        assertNotNull(dialog, "the user is asked before the open issues are closed");
        assertEquals(DqIssueStatus.OPEN, reloadIssue().getStatus(), "nothing is saved until the user agrees");

        Button yes = dialog.getButtons().get(0);
        yes.click();

        assertEquals(DqIssueStatus.RULE_DATA_SOURCE_CHANGED, reloadIssue().getStatus());
        assertEquals("code", dataManager.load(DqRule.class).id(rule.getId()).one().getColumnName());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private DataGrid<DqIssue> openList() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        return UiTestUtils.getComponent(UiTestUtils.getCurrentView(), "dqIssuesDataGrid");
    }

    private DqIssue gridItem(DataGrid<DqIssue> grid) {
        return grid.getGenericDataView().getItems()
                .filter(item -> issue.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> fail("the issue is not listed"));
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(DqIssueViewsUiTest::selfAndDescendants));
    }

    private DqIssue reloadIssue() {
        return dataManager.load(DqIssue.class).id(issue.getId()).fetchPlan(FetchPlan.BASE).one();
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
