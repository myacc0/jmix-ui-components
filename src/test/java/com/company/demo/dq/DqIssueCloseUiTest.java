package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.DqDataDomain;
import com.company.demo.entity.DqIssue;
import com.company.demo.entity.DqRule;
import com.company.demo.enums.DqDimension;
import com.company.demo.enums.DqIssueStatus;
import com.company.demo.enums.DqRuleType;
import com.company.demo.enums.DqSeverity;
import com.company.demo.service.DqIssueService;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqissue.DqIssueCloseView;
import com.company.demo.view.dqissue.DqIssueListView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.View;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Walks the "close issue" flow the way a user does — open the issue list, select an open issue,
 * press the button, pick an outcome and save — and asserts what the press produces: the outcome,
 * the notes and the resolution timestamps stored on the issue.
 * <p>
 * This is the render check the descriptor cannot get from a compiler: the button, the grid action
 * and every dialog field are resolved by id and driven through their bindings, so a broken data
 * container, property path or component id fails here rather than in front of the user.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueCloseUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    DqIssueService dqIssueService;

    private DqDataDomain domain;
    private DqRule rule;
    private DqIssue issue;
    private DqIssue closedIssue;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        domain = dataManager.create(DqDataDomain.class);
        domain.setCode("dq-close-" + suffix);
        domain.setShortName("DQ CLS " + suffix);
        domain.setName("DQ close UI test domain " + suffix);
        domain.setCreatedAt(LocalDateTime.now());
        domain = dataManager.save(domain);

        rule = dataManager.create(DqRule.class);
        rule.setName("dq close ui test rule " + suffix);
        rule.setDataSource("main");
        rule.setTableName("demo_dq_data_domain");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.COMPLETENESS);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{}");
        rule.setSeverity(DqSeverity.MEDIUM);
        rule.setActive(true);
        rule.setDomain(domain);
        rule = dataManager.save(rule);

        issue = dataManager.save(newIssue(DqIssueStatus.OPEN, "dq close ui test issue " + suffix));
        closedIssue = dataManager.save(newIssue(DqIssueStatus.WONTFIX, "dq close ui test closed issue " + suffix));
    }

    @Test
    void closeIssueButtonStoresTheOutcomeAndStampsTheResolution() {
        LocalDateTime beforeClosing = LocalDateTime.now();

        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView listView = UiTestUtils.getCurrentView();

        JmixButton closeIssueButton = UiTestUtils.getComponent(listView, "closeIssueButton");
        assertFalse(closeIssueButton.isEnabled(), "nothing is selected yet, so there is nothing to close");

        DataGrid<DqIssue> issuesDataGrid = UiTestUtils.getComponent(listView, "dqIssuesDataGrid");
        issuesDataGrid.select(gridItem(issuesDataGrid, issue));
        assertTrue(closeIssueButton.isEnabled(), "a selected open issue can be closed");

        closeIssueButton.click();

        DqIssueCloseView closeView = openedDialogView(DqIssueCloseView.class);

        JmixSelect<DqIssueStatus> statusField = UiTestUtils.getComponent(closeView, "statusField");
        assertEquals(List.of(DqIssueStatus.RESOLVED, DqIssueStatus.WONTFIX, DqIssueStatus.FALSE_POSITIVE),
                statusField.getGenericDataView().getItems().toList(),
                "only the closing outcomes are offered");

        JmixButton saveButton = UiTestUtils.getComponent(closeView, "saveButton");
        saveButton.click();
        assertEquals(DqIssueStatus.OPEN, reload(issue).getStatus(),
                "saving without picking an outcome closes nothing");

        statusField.setValue(DqIssueStatus.RESOLVED);
        JmixTextArea notesField = UiTestUtils.getComponent(closeView, "resolutionNotesField");
        notesField.setValue("nulls backfilled from the source system");

        saveButton.click();

        DqIssue stored = reload(issue);
        assertEquals(DqIssueStatus.RESOLVED, stored.getStatus());
        assertEquals("nulls backfilled from the source system", stored.getResolutionNotes());
        assertNotNull(stored.getResolvedAt(), "closing stamps the resolution time");
        assertNotNull(stored.getUpdatedAt(), "closing stamps the update time");
        assertFalse(stored.getResolvedAt().isBefore(beforeClosing), "the resolution time is the closing moment");
    }

    @Test
    void closeIssueButtonStaysDisabledForAnIssueThatIsAlreadyClosed() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView listView = UiTestUtils.getCurrentView();

        JmixButton closeIssueButton = UiTestUtils.getComponent(listView, "closeIssueButton");
        DataGrid<DqIssue> issuesDataGrid = UiTestUtils.getComponent(listView, "dqIssuesDataGrid");

        issuesDataGrid.select(gridItem(issuesDataGrid, closedIssue));
        assertFalse(closeIssueButton.isEnabled(), "a closed issue has an outcome already");

        issuesDataGrid.select(gridItem(issuesDataGrid, issue));
        assertTrue(closeIssueButton.isEnabled(), "an open issue can be closed");
    }

    /** The stamping lives in the service, so it holds for a caller that never goes near the dialog. */
    @Test
    void serviceStampsTheResolutionOnTheIssueItCloses() {
        LocalDateTime beforeClosing = LocalDateTime.now();

        assertNull(issue.getResolvedAt());
        assertNull(issue.getUpdatedAt());

        issue.setStatus(DqIssueStatus.FALSE_POSITIVE);
        dqIssueService.markClosed(issue);
        issue = dataManager.save(issue);

        DqIssue stored = reload(issue);
        assertEquals(DqIssueStatus.FALSE_POSITIVE, stored.getStatus());
        assertNotNull(stored.getResolvedAt());
        assertNotNull(stored.getUpdatedAt());
        assertFalse(stored.getResolvedAt().isBefore(beforeClosing));
    }

    private DqIssue newIssue(DqIssueStatus status, String title) {
        DqIssue newIssue = dataManager.create(DqIssue.class);
        newIssue.setRule(rule);
        newIssue.setDataSource("main");
        newIssue.setStatus(status);
        newIssue.setSeverity(DqSeverity.MEDIUM);
        newIssue.setTitle(title);
        newIssue.setCreatedAt(LocalDateTime.now());
        return newIssue;
    }

    private DqIssue reload(DqIssue stale) {
        return dataManager.load(DqIssue.class).id(stale.getId()).fetchPlan(FetchPlan.BASE).one();
    }

    /** The grid's own instance of the given issue — the one its selection model can match. */
    private DqIssue gridItem(DataGrid<DqIssue> issuesDataGrid, DqIssue expected) {
        return issuesDataGrid.getGenericDataView().getItems()
                .filter(item -> expected.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> fail("issue " + expected.getId() + " is not shown in the issue list"));
    }

    /**
     * The view opened in a dialog window. The dialog is attached to the UI only when the pending
     * client response runs, which a headless UI test has to trigger itself.
     */
    private <V extends View<?>> V openedDialogView(Class<V> viewClass) {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return findDescendant(ui, viewClass)
                .orElseGet(() -> fail(viewClass.getSimpleName() + " is not opened"));
    }

    private <V> Optional<V> findDescendant(Component component, Class<V> componentClass) {
        return component.getChildren()
                .map(child -> componentClass.isInstance(child)
                        ? Optional.of(componentClass.cast(child))
                        : findDescendant(child, componentClass))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @AfterEach
    void tearDown() {
        dataManager.remove(closedIssue);
        dataManager.remove(issue);
        dataManager.remove(rule);
        dataManager.remove(domain);
    }
}
