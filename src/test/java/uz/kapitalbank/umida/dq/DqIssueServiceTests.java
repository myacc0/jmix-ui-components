package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.SystemAuthenticator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqIssueClosingReason;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.service.dq.DqRuleService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue bookkeeping that does not need a check run: the generated code, who may close an issue
 * and who is recorded as having closed it, the subdivision an issue is assigned to, the issues a
 * rule closes when it is moved to other data or reassigns when it is assigned to another subdivision,
 * and that a closed issue always carries its closing reason and time.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueServiceTests {

    @Autowired
    DataManager dataManager;

    @Autowired
    DqIssueService issueService;

    @Autowired
    DqRuleService ruleService;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    private DqTestData testData;
    private OrgStructureSubdivision assignee;
    private DqRule rule;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();
        assignee = createSubdivision();
        rule = testData.createRule(domain, "issue service", DqRuleType.NOT_NULL, "short_name", "{}",
                r -> r.setAssignee(assignee));
    }

    @Test
    void aNewIssueGetsTheNextCode() {
        DqIssue first = createOpenIssue();
        DqIssue second = createOpenIssue();

        assertTrue(first.getCode().matches("DQI-\\d+"), first.getCode());
        assertTrue(second.getCode().matches("DQI-\\d+"), second.getCode());
        assertEquals(number(first) + 1, number(second), "the codes come from a sequence with step 1");
        assertTrue(number(first) >= 100, "the sequence starts at 100");
        assertNotNull(first.getCreatedAt());
    }

    @Test
    void onlyAUserOfTheAssigneeSubdivisionMayCloseAnOpenIssue() {
        DqIssue issue = createOpenIssue();

        User member = createUserIn(assignee);
        User outsider = createUserIn(createSubdivision());
        User withoutEmployee = createUser(null);

        assertTrue(canClose(member, issue), "an employee of the assignee subdivision may close it");
        assertFalse(canClose(outsider, issue), "an employee of another subdivision may not");
        assertFalse(canClose(withoutEmployee, issue), "a user without an employee may not");
        assertFalse(issueService.canClose(issue), "admin has no employee, so it may not either");

        DqIssue closed = createClosedIssue(DqIssueClosingReason.WONTFIX);
        assertFalse(canClose(member, closed), "a closed issue cannot be closed again");
    }

    @Test
    void closingByHandRecordsTheEmployeeOfTheUser() {
        DqIssue issue = createOpenIssue();
        User member = createUserIn(assignee);

        issue.setClosingReason(DqIssueClosingReason.FALSE_POSITIVE);
        // only the closing is the member's; the test user has no roles to save with
        systemAuthenticator.runWithUser(member.getUsername(), () -> issueService.markAsManualClosed(issue));
        dataManager.save(issue);

        DqIssue closed = reloadWithClosedBy(issue);
        assertEquals(DqIssueStatus.CLOSED, closed.getStatus());
        assertEquals(DqIssueClosingReason.FALSE_POSITIVE, closed.getClosingReason());
        assertNotNull(closed.getClosedAt());
        assertNotNull(closed.getClosedBy(), "a closing by hand records who closed the issue");
        assertEquals(member.getEmployee().getId(), closed.getClosedBy().getId());
    }

    @Test
    void movingTheRuleToOtherDataClosesItsOpenIssues() {
        DqIssue open = createOpenIssue();
        DqIssue dismissed = createClosedIssue(DqIssueClosingReason.FALSE_POSITIVE);

        DqRule renamed = reloadRule();
        renamed.setName(renamed.getName() + " renamed");
        assertFalse(ruleService.isDataSourceChanged(renamed));
        rule = dataManager.save(renamed);
        assertEquals(DqIssueStatus.OPEN, reload(open).getStatus(), "a rename leaves the data as it was");

        DqRule moved = reloadRule();
        moved.setColumnName("code");
        assertTrue(ruleService.isDataSourceChanged(moved));
        rule = dataManager.save(moved);

        DqIssue closed = reloadWithClosedBy(open);
        assertEquals(DqIssueStatus.CLOSED, closed.getStatus());
        assertEquals(DqIssueClosingReason.RULE_DATA_SOURCE_CHANGED, closed.getClosingReason());
        assertNotNull(closed.getClosedAt());
        assertNull(closed.getClosedBy(), "a closing that is not by hand records no one");
        assertEquals(DqIssueClosingReason.FALSE_POSITIVE, reload(dismissed).getClosingReason(),
                "an issue closed already keeps its reason");
    }

    @Test
    void aNewIssueTakesTheAssigneeOfItsRule() {
        assertEquals(assignee.getId(), reloadWithAssignee(createOpenIssue()).getAssignee().getId());

        // a check run holds its rule loaded without the assignee
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(dataManager.load(DqRule.class).id(rule.getId()).fetchPlan(FetchPlan.BASE).one());
        DqIssue saved = testData.track(dataManager.save(issue));
        assertEquals(assignee.getId(), reloadWithAssignee(saved).getAssignee().getId());
    }

    @Test
    void reassigningTheRuleMovesItsOpenIssuesAndKeepsTheirDueDate() {
        OrgStructureSubdivision newAssignee = createSubdivision();
        User oldMember = createUserIn(assignee);
        User newMember = createUserIn(newAssignee);
        LocalDate dueDate = LocalDate.now().minusDays(3);
        DqIssue open = createOpenIssue(dueDate);
        DqIssue dismissed = createClosedIssue(DqIssueClosingReason.WONTFIX);

        DqRule reassigned = reloadRule();
        reassigned.setAssignee(newAssignee);
        assertTrue(ruleService.isAssigneeChanged(reassigned));
        assertFalse(ruleService.isDataSourceChanged(reassigned));
        rule = dataManager.save(reassigned);

        DqIssue moved = reloadWithAssignee(open);
        assertEquals(DqIssueStatus.OPEN, moved.getStatus(), "a reassignment leaves the issue open");
        assertEquals(newAssignee.getId(), moved.getAssignee().getId());
        assertEquals(dueDate, moved.getDueDate(), "a reassignment does not give the issue more time");
        assertEquals(assignee.getId(), reloadWithAssignee(dismissed).getAssignee().getId(),
                "a closed issue keeps the subdivision it had");

        assertTrue(canClose(newMember, moved), "the new subdivision may close the issue");
        assertFalse(canClose(oldMember, moved), "the former one may not any more");
    }

    @Test
    void movingAndReassigningTheRuleAtOnceClosesItsIssuesUnderTheFormerAssignee() {
        DqIssue open = createOpenIssue();

        DqRule changed = reloadRule();
        changed.setAssignee(createSubdivision());
        changed.setColumnName("code");
        assertTrue(ruleService.isAssigneeChanged(changed));
        rule = dataManager.save(changed);

        DqIssue closed = reloadWithAssignee(open);
        assertEquals(DqIssueClosingReason.RULE_DATA_SOURCE_CHANGED, closed.getClosingReason());
        assertEquals(assignee.getId(), closed.getAssignee().getId(),
                "the closing goes first, so the closed issue stays with the former assignee");
    }

    @Test
    void aNewIssueIsOpenWithoutAClosingReason() {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        DqIssue saved = testData.track(dataManager.save(issue));

        assertEquals(DqIssueStatus.OPEN, saved.getStatus());
        assertNull(saved.getClosingReason());
        assertNull(saved.getClosedAt());
    }

    @Test
    void theDatabaseRejectsAClosingWithoutItsReasonOrTime() {
        DqIssue withoutReason = dataManager.create(DqIssue.class);
        withoutReason.setRule(rule);
        withoutReason.setStatus(DqIssueStatus.CLOSED);
        withoutReason.setClosedAt(OffsetDateTime.now());
        assertThrows(RuntimeException.class, () -> dataManager.save(withoutReason));

        DqIssue withoutTime = dataManager.create(DqIssue.class);
        withoutTime.setRule(rule);
        withoutTime.setStatus(DqIssueStatus.CLOSED);
        withoutTime.setClosingReason(DqIssueClosingReason.WONTFIX);
        assertThrows(RuntimeException.class, () -> dataManager.save(withoutTime));

        DqIssue openWithReason = dataManager.create(DqIssue.class);
        openWithReason.setRule(rule);
        openWithReason.setStatus(DqIssueStatus.OPEN);
        openWithReason.setClosingReason(DqIssueClosingReason.FIXED);
        assertThrows(RuntimeException.class, () -> dataManager.save(openWithReason));
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private boolean canClose(User user, DqIssue issue) {
        return systemAuthenticator.withUser(user.getUsername(), () -> issueService.canClose(issue));
    }

    private DqIssue createOpenIssue() {
        return createOpenIssue(null);
    }

    private DqIssue createOpenIssue(LocalDate dueDate) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        issue.setDueDate(dueDate);
        return testData.track(dataManager.save(issue));
    }

    private DqIssue createClosedIssue(DqIssueClosingReason reason) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setClosingReason(reason);
        issueService.markClosed(issue);
        return testData.track(dataManager.save(issue));
    }

    private OrgStructureSubdivision createSubdivision() {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName("DQ issue assignee " + suffix());
        return testData.track(dataManager.save(subdivision));
    }

    /** A user whose employee holds a position in the subdivision. */
    private User createUserIn(OrgStructureSubdivision subdivision) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("DQ issue employee " + suffix());
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

        return createUser(employee);
    }

    private User createUser(OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        user.setUsername("dq-issue-test-" + UUID.randomUUID());
        user.setFirstName("Dq");
        user.setLastName("Issue");
        user.setEmployee(employee);
        return testData.track(dataManager.save(user));
    }

    private DqRule reloadRule() {
        return dataManager.load(DqRule.class).id(rule.getId()).one();
    }

    private DqIssue reload(DqIssue issue) {
        return dataManager.load(DqIssue.class).id(issue.getId()).fetchPlan(FetchPlan.BASE).one();
    }

    private DqIssue reloadWithClosedBy(DqIssue issue) {
        return dataManager.load(DqIssue.class).id(issue.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("closedBy", FetchPlan.BASE))
                .one();
    }

    private DqIssue reloadWithAssignee(DqIssue issue) {
        return dataManager.load(DqIssue.class).id(issue.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("assignee", FetchPlan.BASE))
                .one();
    }

    private static long number(DqIssue issue) {
        return Long.parseLong(issue.getCode().substring(DqIssueService.CODE_PREFIX.length()));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
