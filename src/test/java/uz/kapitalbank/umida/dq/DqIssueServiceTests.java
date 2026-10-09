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
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.service.dq.DqRuleService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue bookkeeping that does not need a check run: the generated code, who may close an issue,
 * and the issues a rule closes when it is moved to other data.
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
        DqIssue first = createIssue(DqIssueStatus.OPEN);
        DqIssue second = createIssue(DqIssueStatus.OPEN);

        assertTrue(first.getCode().matches("DQI-\\d+"), first.getCode());
        assertTrue(second.getCode().matches("DQI-\\d+"), second.getCode());
        assertEquals(number(first) + 1, number(second), "the codes come from a sequence with step 1");
        assertTrue(number(first) >= 100, "the sequence starts at 100");
        assertNotNull(first.getCreatedAt());
    }

    @Test
    void onlyAUserOfTheAssigneeSubdivisionMayCloseAnOpenIssue() {
        DqIssue issue = createIssue(DqIssueStatus.OPEN);

        User member = createUserIn(assignee);
        User outsider = createUserIn(createSubdivision());
        User withoutEmployee = createUser(null);

        assertTrue(canClose(member, issue), "an employee of the assignee subdivision may close it");
        assertFalse(canClose(outsider, issue), "an employee of another subdivision may not");
        assertFalse(canClose(withoutEmployee, issue), "a user without an employee may not");
        assertFalse(issueService.canClose(issue), "admin has no employee, so it may not either");

        DqIssue closed = createIssue(DqIssueStatus.WONTFIX);
        assertFalse(canClose(member, closed), "a closed issue cannot be closed again");
    }

    @Test
    void movingTheRuleToOtherDataClosesItsOpenIssues() {
        DqIssue open = createIssue(DqIssueStatus.OPEN);
        DqIssue dismissed = createIssue(DqIssueStatus.FALSE_POSITIVE);

        DqRule renamed = reloadRule();
        renamed.setName(renamed.getName() + " renamed");
        assertFalse(ruleService.isDataSourceChanged(renamed));
        rule = dataManager.save(renamed);
        assertEquals(DqIssueStatus.OPEN, reload(open).getStatus(), "a rename leaves the data as it was");

        DqRule moved = reloadRule();
        moved.setColumnName("code");
        assertTrue(ruleService.isDataSourceChanged(moved));
        rule = dataManager.save(moved);

        DqIssue closed = reload(open);
        assertEquals(DqIssueStatus.RULE_DATA_SOURCE_CHANGED, closed.getStatus());
        assertNotNull(closed.getResolvedAt());
        assertEquals(DqIssueStatus.FALSE_POSITIVE, reload(dismissed).getStatus(),
                "an issue closed already keeps its outcome");
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private boolean canClose(User user, DqIssue issue) {
        return systemAuthenticator.withUser(user.getUsername(), () -> issueService.canClose(issue));
    }

    private DqIssue createIssue(DqIssueStatus status) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(status);
        if (status != DqIssueStatus.OPEN) {
            issue.setResolvedAt(OffsetDateTime.now());
        }
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
        position.setEmployee(employee);
        testData.track(dataManager.save(position));

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
