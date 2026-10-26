package uz.kapitalbank.umida.dq;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.util.OperationResult;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.dto.dq.DqIssueTreeItem;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.*;
import uz.kapitalbank.umida.security.DqIssueAssigneeRole;
import uz.kapitalbank.umida.security.DqIssueOwnSubdivisionRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;
import uz.kapitalbank.umida.view.dq.DqCheckRunViolationsView;
import uz.kapitalbank.umida.view.dq.DqIssueCloseView;
import uz.kapitalbank.umida.view.dq.DqIssueDetailView;
import uz.kapitalbank.umida.view.dq.DqIssueListView;
import uz.kapitalbank.umida.view.dq.DqViolationsFragment;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue screens as an employee with {@link DqIssueAssigneeRole} and {@link DqIssueOwnSubdivisionRole}
 * (and {@link UiMinimalRole}, which every LDAP user gets) sees them: only the issues of the own
 * subdivision are listed, and such an issue is opened, its violating rows shown, and closed.
 */
@UiTest
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueAccessUiTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    ViewNavigators viewNavigators;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    private DqTestData testData;
    private DqIssue ownIssue;
    private User member;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();
        OrgStructureSubdivision ownSubdivision = createSubdivision();
        OrgStructureSubdivision otherSubdivision = createSubdivision();

        DqRule ownRule = testData.createRule(domain, "own access ui", DqRuleType.NOT_NULL, "short_name", "{}",
                r -> r.setAssignee(ownSubdivision));
        DqRule otherRule = testData.createRule(domain, "other access ui", DqRuleType.NOT_NULL, "short_name", "{}",
                r -> r.setAssignee(otherSubdivision));

        ownIssue = createOpenIssue(ownRule, createFailedResult(ownRule));
        createOpenIssue(otherRule, null);

        member = createUserIn(ownSubdivision);
        assignRole(UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(DqIssueAssigneeRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(DqIssueOwnSubdivisionRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);

        systemAuthenticator.begin(member.getUsername());
    }

    @AfterEach
    void tearDown() {
        systemAuthenticator.end();
        testData.cleanup();
    }

    @Test
    void theListShowsOnlyTheIssuesOfTheOwnSubdivision() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView view = UiTestUtils.getCurrentView();
        DataGrid<DqIssue> grid = UiTestUtils.getComponent(view, "dqIssuesDataGrid");

        List<DqIssue> shown = grid.getGenericDataView().getItems().toList();
        assertEquals(List.of(ownIssue.getId()), shown.stream().map(DqIssue::getId).toList());
        assertNotNull(shown.get(0).getRule().getCode(), "the rule columns are filled");
        assertNotNull(shown.get(0).getAssignee().getName(), "the assignee column is filled");
        assertNotNull(grid.getColumnByKey("ruleGroup"), "the member sees the rule group column");
        assertNotNull(grid.getColumnByKey("ruleDomain"), "the member sees the rule domain column");
        assertNotNull(shown.get(0).getRule().getDomain().getShortName(), "the domain column is filled");

        grid.select(shown.get(0));
        JmixButton closeIssueButton = UiTestUtils.getComponent(view, "closeIssueButton");
        JmixButton violationsButton = UiTestUtils.getComponent(view, "violationsButton");
        assertTrue(closeIssueButton.isVisible() && closeIssueButton.isEnabled(), "a member may close the issue of the own subdivision");
        assertTrue(violationsButton.isEnabled());

        violationsButton.click();
        UI.getCurrent().getInternals().getStateTree().runExecutionsBeforeClientResponse();
        DqCheckRunViolationsView violationsView = UI.getCurrent().getChildren()
                .flatMap(DqIssueAccessUiTest::selfAndDescendants)
                .filter(DqCheckRunViolationsView.class::isInstance)
                .map(DqCheckRunViolationsView.class::cast)
                .findFirst()
                .orElseGet(() -> fail("the violations dialog is open"));
        DqViolationsFragment fragment = UiTestUtils.getComponent(violationsView, "violationsFragment");
        Grid<?> violationsGrid = (Grid<?>) UiComponentUtils.getComponent(fragment, "violationsDataGrid");
        assertEquals(3, violationsGrid.getGenericDataView().getItems().count());
    }

    @Test
    void theTreeShowsOnlyTheIssuesOfTheOwnSubdivisionGroupedByWhatTheMemberMaySee() {
        viewNavigators.view(UiTestUtils.getCurrentView(), DqIssueListView.class).navigate();
        DqIssueListView view = UiTestUtils.getCurrentView();
        JmixSelect<DqIssueGrouping> groupingField = UiTestUtils.getComponent(view, "groupingField");

        assertEquals(List.of(DqIssueGrouping.values()), groupingField.getListDataView().getItems().toList(),
                "the member may see every attribute the issues are grouped by");

        groupingField.setValue(DqIssueGrouping.ASSIGNEE);
        TreeDataGrid<DqIssueTreeItem> tree = UiTestUtils.getComponent(view, "dqIssuesTreeDataGrid");
        List<DqIssueTreeItem> items = List.copyOf(tree.getItems().getItems());
        assertEquals(2, items.size(), "the own subdivision with the own issue: " + items);
        assertEquals(ownIssue.getId(), items.get(1).getIssue().getId());
        assertNotNull(tree.getColumnByKey("ruleCode"), "the member sees the rule columns of the tree");

        tree.select(items.get(1));
        JmixButton closeIssueButton = UiTestUtils.getComponent(view, "closeIssueButton");
        assertTrue(closeIssueButton.isVisible() && closeIssueButton.isEnabled(),
                "a member may close the issue of the own subdivision from the tree");
    }

    @Test
    void anIssueOfTheOwnSubdivisionIsOpenedAndClosed() {
        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqIssue.class)
                .editEntity(ownIssue)
                .withViewClass(DqIssueDetailView.class)
                .navigate();
        DqIssueDetailView detail = UiTestUtils.getCurrentView();
        JmixButton closeIssueButton = UiTestUtils.getComponent(detail, "closeIssueButton");
        assertTrue(closeIssueButton.isVisible() && closeIssueButton.isEnabled());

        viewNavigators.detailView(UiTestUtils.getCurrentView(), DqIssue.class)
                .editEntity(ownIssue)
                .withViewClass(DqIssueCloseView.class)
                .navigate();
        DqIssueCloseView close = UiTestUtils.getCurrentView();
        JmixSelect<DqIssueClosingReason> closingReasonField = UiTestUtils.getComponent(close, "closingReasonField");
        assertFalse(closingReasonField.isReadOnly(), "the reason is editable for the member");
        closingReasonField.setValue(DqIssueClosingReason.FALSE_POSITIVE);
        assertEquals(OperationResult.Status.SUCCESS, close.closeWithSave().getStatus());

        DqIssue closed = dataManager.load(DqIssue.class).id(ownIssue.getId()).one();
        assertEquals(DqIssueStatus.CLOSED, closed.getStatus());
        assertEquals(DqIssueClosingReason.FALSE_POSITIVE, closed.getClosingReason());
    }

    private static Stream<Component> selfAndDescendants(Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(DqIssueAccessUiTest::selfAndDescendants));
    }

    // ---------------------------------------------------------------------
    // fixtures, created as admin
    // ---------------------------------------------------------------------

    private DqCheckRunResult createFailedResult(DqRule rule) {
        DqCheckRun run = dataManager.create(DqCheckRun.class);
        run.setTriggered(DqCheckRunTrigger.SYSTEM);
        run.setStartedAt(OffsetDateTime.now());
        run.setStatus(DqCheckRunStatus.SUCCESS);
        run.setRulesTotal(1);
        run = testData.track(dataManager.save(run));

        DqCheckRunResult failed = dataManager.create(DqCheckRunResult.class);
        failed.setCheckRun(run);
        failed.setDataSource("main");
        failed.setRule(rule);
        failed.setStatus(DqCheckResultStatus.FAILED);
        failed.setTotalRecords(BigInteger.valueOf(100));
        failed.setFailedRecords(BigInteger.valueOf(3));
        failed.setPassRate(new BigDecimal("97.00"));
        failed.setViolationsQuery("SELECT 'code-' || g AS code FROM generate_series(1, 3) g");
        return testData.track(dataManager.save(failed));
    }

    private DqIssue createOpenIssue(DqRule rule, DqCheckRunResult checkResult) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setCheckResult(checkResult);
        issue.setStatus(DqIssueStatus.OPEN);
        return testData.track(dataManager.save(issue));
    }

    private OrgStructureSubdivision createSubdivision() {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName("DQ access ui " + UUID.randomUUID().toString().substring(0, 8));
        return testData.track(dataManager.save(subdivision));
    }

    private User createUserIn(OrgStructureSubdivision subdivision) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("DQ access ui employee");
        employee = testData.track(dataManager.save(employee));

        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setId(UUID.randomUUID().toString());
        position.setSubdivision(subdivision);
        position = testData.track(dataManager.save(position));

        OrgStructureAssignments assignment = dataManager.create(OrgStructureAssignments.class);
        assignment.setId(UUID.randomUUID().toString());
        assignment.setPosition(position);
        assignment.setEmployee(employee);
        assignment.setStartDate(LocalDate.now().minusYears(1));
        testData.track(dataManager.save(assignment));

        User user = dataManager.create(User.class);
        user.setUsername("dq-access-ui-" + UUID.randomUUID());
        user.setFirstName("Dq");
        user.setLastName("Access");
        user.setEmployee(employee);
        return testData.track(dataManager.save(user));
    }

    private void assignRole(String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(member.getUsername());
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        testData.track(dataManager.save(assignment));
    }
}
