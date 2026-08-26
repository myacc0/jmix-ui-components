package com.company.demo.dq;

import com.company.demo.DemoApplication;
import com.company.demo.component.DqBadges;
import com.company.demo.entity.dq.DqCheckRun;
import com.company.demo.entity.dq.DqDataDomain;
import com.company.demo.entity.dq.DqIssue;
import com.company.demo.entity.dq.DqRule;
import com.company.demo.enums.dq.DqCheckRunStatus;
import com.company.demo.enums.dq.DqDimension;
import com.company.demo.enums.dq.DqIssueStatus;
import com.company.demo.enums.dq.DqRuleType;
import com.company.demo.enums.dq.DqSeverity;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import com.company.demo.view.dqcheckrun.DqCheckRunListView;
import com.company.demo.view.dqissue.DqIssueListView;
import com.company.demo.view.dqrule.DqRuleListView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGrid;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enum columns of the DQ list views render their value as a badge. The stylesheet cannot be
 * exercised here, but the part the Java side owns can: that a renderer is wired to each column, and
 * that the badge it builds carries the shape class plus the value class the CSS keys on.
 */
@UiTest
@SpringBootTest(classes = {DemoApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqBadgeColumnsUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    Messages messages;

    private DqDataDomain domain;
    private DqRule rule;
    private DqIssue issue;
    private DqCheckRun checkRun;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        domain = dataManager.create(DqDataDomain.class);
        domain.setCode("dq-badge-" + suffix);
        domain.setShortName("DQ BDG " + suffix);
        domain.setName("DQ badge UI test domain " + suffix);
        domain.setCreatedAt(LocalDateTime.now());
        domain = dataManager.save(domain);

        rule = dataManager.create(DqRule.class);
        rule.setName("dq badge ui test rule " + suffix);
        rule.setDataSource("main");
        rule.setTableName("demo_dq_data_domain");
        rule.setColumnName("name");
        rule.setDimension(DqDimension.VALIDITY);
        rule.setRuleType(DqRuleType.NOT_NULL);
        rule.setRuleConfig("{}");
        rule.setSeverity(DqSeverity.CRITICAL);
        rule.setActive(true);
        rule.setDomain(domain);
        rule = dataManager.save(rule);

        issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setDataSource("main");
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setSeverity(DqSeverity.MEDIUM);
        issue.setTitle("dq badge ui test issue " + suffix);
        issue.setCreatedAt(LocalDateTime.now());
        issue = dataManager.save(issue);

        checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource("main");
        checkRun.setTriggeredUsername("admin");
        checkRun.setStartedAt(LocalDateTime.now());
        checkRun.setStatus(DqCheckRunStatus.SUCCESS);
        checkRun.setRulesTotal(1);
        checkRun = dataManager.save(checkRun);
    }

    @Test
    void ruleListBadgesDimensionAndSeverity() {
        DataGrid<DqRule> grid = openGrid(DqRuleListView.class, "dqRulesDataGrid");

        assertBadge(grid, "dimension", rule, DqBadges.DIMENSION + "validity",
                messages.getMessage(DqDimension.VALIDITY));
        assertBadge(grid, "severity", rule, DqBadges.SEVERITY + "critical",
                messages.getMessage(DqSeverity.CRITICAL));
    }

    @Test
    void issueListBadgesStatusAndSeverity() {
        DataGrid<DqIssue> grid = openGrid(DqIssueListView.class, "dqIssuesDataGrid");

        assertBadge(grid, "status", issue, DqBadges.ISSUE_STATUS + "open",
                messages.getMessage(DqIssueStatus.OPEN));
        assertBadge(grid, "severity", issue, DqBadges.SEVERITY + "medium",
                messages.getMessage(DqSeverity.MEDIUM));
    }

    @Test
    void checkRunListBadgesStatus() {
        DataGrid<DqCheckRun> grid = openGrid(DqCheckRunListView.class, "dqCheckRunsDataGrid");

        assertBadge(grid, "status", checkRun, DqBadges.RUN_STATUS + "success",
                messages.getMessage(DqCheckRunStatus.SUCCESS));
    }

    private <V extends View<?>, E> DataGrid<E> openGrid(Class<V> viewClass, String gridId) {
        viewNavigators.view(UiTestUtils.getCurrentView(), viewClass).navigate();
        V view = UiTestUtils.getCurrentView();
        return UiTestUtils.getComponent(view, gridId);
    }

    private <E> void assertBadge(DataGrid<E> grid, String columnKey, E item,
                                 String expectedValueClass, String expectedText) {
        Grid.Column<E> column = grid.getColumnByKey(columnKey);
        Renderer<E> renderer = column.getRenderer();
        ComponentRenderer<?, E> componentRenderer =
                assertInstanceOf(ComponentRenderer.class, renderer, columnKey + " is rendered as a component");

        Component badge = componentRenderer.createComponent(item);
        assertEquals(expectedText, badge.getElement().getText());
        assertTrue(badge.getElement().getClassList().contains("dq-badge"),
                columnKey + " badge carries the shape class");
        assertTrue(badge.getElement().getClassList().contains(expectedValueClass),
                columnKey + " badge carries " + expectedValueClass);
    }

    @AfterEach
    void tearDown() {
        dataManager.remove(checkRun);
        dataManager.remove(issue);
        dataManager.remove(rule);
        dataManager.remove(domain);
    }
}
