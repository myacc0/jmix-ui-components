package uz.kapitalbank.umida.dq;

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
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckRunListView;
import uz.kapitalbank.umida.view.dq.DqIssueListView;
import uz.kapitalbank.umida.view.dq.DqRuleListView;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The enum columns of the DQ list views render their value as a badge. The stylesheet cannot be
 * exercised here, but the part the Java side owns can: that a renderer is wired to each column, and
 * that the badge it builds carries the shape class plus the value class the CSS keys on.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqBadgeColumnsUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    Messages messages;

    private DqTestData testData;
    private DictDataDomain domain;
    private DqRule rule;
    private DqIssue issue;
    private DqCheckRun checkRun;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        domain = testData.createDomain();
        rule = testData.createRule(domain, "badge ui", DqRuleType.NOT_NULL, "short_name", "{}", r -> {
            r.setDimension(DqDimension.VALIDITY);
            r.setSeverity(DqSeverity.CRITICAL);
        });

        issue = dataManager.create(DqIssue.class);
        issue.setCode("dq-badge-" + UUID.randomUUID().toString().substring(0, 8));
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setCreatedAt(OffsetDateTime.now());
        issue = dataManager.save(issue);

        checkRun = dataManager.create(DqCheckRun.class);
        checkRun.setDataSource("main");
        checkRun.setTriggered(DqCheckRunTrigger.SYSTEM);
        checkRun.setStartedAt(OffsetDateTime.now());
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
        if (checkRun != null) {
            dataManager.remove(checkRun);
        }
        if (issue != null) {
            dataManager.remove(issue);
        }
        testData.cleanup();
    }
}
