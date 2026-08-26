package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.entity.dq.DqIssue;
import com.company.demo.entity.dq.DqRule;
import com.company.demo.enums.dq.DqDimension;
import com.company.demo.enums.dq.DqIssueStatus;
import com.company.demo.enums.dq.DqRuleType;
import com.company.demo.enums.dq.DqSeverity;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqissue.DqIssueAssigneeView;
import com.company.demo.view.dqissue.DqIssueListView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Walks the "set assignee" flow the way a user does — open the issue list, select an issue, press
 * the button, fill the dialog and save — and asserts what the press produces: the assignee stored on
 * the issue.
 * <p>
 * This is the render check the descriptor cannot get from a compiler: the button, the grid action
 * and every dialog field are resolved by id and driven through their bindings, so a broken data
 * container, property path or component id fails here rather than in front of the user.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueAssigneeUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    private DqDataDomain domain;
    private DqRule rule;
    private DqIssue issue;
    private DqIssue resolvedIssue;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        domain = dataManager.create(DqDataDomain.class);
        domain.setCode("dq-assignee-" + suffix);
        domain.setShortName("DQ ASG " + suffix);
        domain.setName("DQ assignee UI test domain " + suffix);
        domain.setCreatedAt(LocalDateTime.now());
        domain = dataManager.save(domain);

        rule = dataManager.create(DqRule.class);
        rule.setName("dq assignee ui test rule " + suffix);
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

        issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setDataSource("main");
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setSeverity(DqSeverity.MEDIUM);
        issue.setTitle("dq assignee ui test issue " + suffix);
        issue.setCreatedAt(LocalDateTime.now());
        issue = dataManager.save(issue);

        resolvedIssue = dataManager.create(DqIssue.class);
        resolvedIssue.setRule(rule);
        resolvedIssue.setDataSource("main");
        resolvedIssue.setStatus(DqIssueStatus.RESOLVED);
        resolvedIssue.setSeverity(DqSeverity.MEDIUM);
        resolvedIssue.setTitle("dq assignee ui test resolved issue " + suffix);
        resolvedIssue.setCreatedAt(LocalDateTime.now());
        resolvedIssue = dataManager.save(resolvedIssue);
    }

    @Test
    void setAssigneeButtonStoresTheAssigneeOnTheSelectedIssue() {
        LocalDateTime beforeAssigning = LocalDateTime.now();
        assertNull(issue.getUpdatedAt(), "the issue has not been touched yet");

        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView listView = UiTestUtils.getCurrentView();

        JmixButton setAssigneeButton = UiTestUtils.getComponent(listView, "setAssigneeButton");
        assertFalse(setAssigneeButton.isEnabled(), "nothing is selected yet, so there is nobody to assign");

        DataGrid<DqIssue> issuesDataGrid = UiTestUtils.getComponent(listView, "dqIssuesDataGrid");
        issuesDataGrid.select(gridItem(issuesDataGrid, issue));
        assertTrue(setAssigneeButton.isEnabled(), "a selected open issue makes the assignment possible");

        setAssigneeButton.click();

        DqIssueAssigneeView assigneeView = openedDialogView(DqIssueAssigneeView.class);
        assertEquals(2, UiTestUtils.validateView(assigneeView).getAll().size(),
                "name and email are required, so an empty dialog cannot be saved");

        TypedTextField<String> nameField = UiTestUtils.getComponent(assigneeView, "assigneNameField");
        TypedTextField<String> emailField = UiTestUtils.getComponent(assigneeView, "assigneeEmailField");
        TypedTextField<String> phoneField = UiTestUtils.getComponent(assigneeView, "assigneePhoneField");
        TypedDatePicker<LocalDate> dueDateField = UiTestUtils.getComponent(assigneeView, "dueDateField");

        nameField.setValue("Data Steward");
        emailField.setValue("steward@example.com");
        phoneField.setValue("+7 000 000-00-00");
        dueDateField.setValue(LocalDate.now().plusDays(7));

        JmixButton saveButton = UiTestUtils.getComponent(assigneeView, "saveButton");
        saveButton.click();

        DqIssue stored = dataManager.load(DqIssue.class)
                .id(issue.getId())
                .fetchPlan(FetchPlan.BASE)
                .one();
        assertEquals("Data Steward", stored.getAssigneName());
        assertEquals("steward@example.com", stored.getAssigneeEmail());
        assertEquals("+7 000 000-00-00", stored.getAssigneePhone());
        assertEquals(LocalDate.now().plusDays(7), stored.getDueDate());
        assertNotNull(stored.getUpdatedAt(), "assigning stamps the update time");
        assertFalse(stored.getUpdatedAt().isBefore(beforeAssigning), "the update time is the assignment moment");
        assertNull(stored.getResolvedAt(), "assigning does not resolve the issue");
    }

    @Test
    void setAssigneeButtonStaysDisabledForAnIssueThatIsNotOpen() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView listView = UiTestUtils.getCurrentView();

        JmixButton setAssigneeButton = UiTestUtils.getComponent(listView, "setAssigneeButton");
        DataGrid<DqIssue> issuesDataGrid = UiTestUtils.getComponent(listView, "dqIssuesDataGrid");

        issuesDataGrid.select(gridItem(issuesDataGrid, resolvedIssue));
        assertFalse(setAssigneeButton.isEnabled(), "a resolved issue is not assignable");

        // and the rule follows the selection rather than latching on the first row picked
        issuesDataGrid.select(gridItem(issuesDataGrid, issue));
        assertTrue(setAssigneeButton.isEnabled(), "an open issue is assignable");
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
        dataManager.remove(resolvedIssue);
        dataManager.remove(issue);
        dataManager.remove(rule);
        dataManager.remove(domain);
    }
}
