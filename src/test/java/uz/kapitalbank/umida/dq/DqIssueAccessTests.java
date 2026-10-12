package uz.kapitalbank.umida.dq;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
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
import uz.kapitalbank.umida.enums.dq.DqIssueClosingReason;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.security.DqIssueAssigneeRole;
import uz.kapitalbank.umida.security.DqIssueOwnSubdivisionRole;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.DqTestData;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A user with {@link DqIssueAssigneeRole} and {@link DqIssueOwnSubdivisionRole} reads, and closes, only
 * the issues assigned to the subdivision of their employee; {@code admin} still reads all of them.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class DqIssueAccessTests {

    @Autowired
    DataManager dataManager;

    @Autowired
    DqIssueService issueService;

    @Autowired
    SystemAuthenticator systemAuthenticator;

    private DqTestData testData;
    private OrgStructureSubdivision ownSubdivision;
    private DqIssue ownIssue;
    private DqIssue otherIssue;

    @BeforeEach
    void setUp() {
        testData = new DqTestData(dataManager);
        DictDataDomain domain = testData.createDomain();
        ownSubdivision = createSubdivision();
        OrgStructureSubdivision otherSubdivision = createSubdivision();
        ownIssue = createOpenIssue(testData.createRule(domain, "own access", DqRuleType.NOT_NULL,
                "short_name", "{}", r -> r.setAssignee(ownSubdivision)));
        otherIssue = createOpenIssue(testData.createRule(domain, "other access", DqRuleType.NOT_NULL,
                "short_name", "{}", r -> r.setAssignee(otherSubdivision)));
    }

    @Test
    void aMemberReadsOnlyTheIssuesOfTheirSubdivision() {
        User member = withRoles(createUserIn(ownSubdivision));

        assertEquals(Set.of(ownIssue.getId()), visibleIssueIds(member));
        assertTrue(systemAuthenticator.withUser(member.getUsername(),
                () -> dataManager.load(DqIssue.class).id(otherIssue.getId()).optional()).isEmpty(),
                "an issue of another subdivision is not found even by its id");

        DqIssue loaded = systemAuthenticator.withUser(member.getUsername(),
                () -> dataManager.load(DqIssue.class).id(ownIssue.getId())
                        .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                                .add("rule", FetchPlan.INSTANCE_NAME)
                                .add("assignee", FetchPlan.INSTANCE_NAME))
                        .one());
        assertNotNull(loaded.getRule(), "the rule of the issue is readable");
        assertEquals(ownSubdivision.getName(), loaded.getAssignee().getName());
    }

    @Test
    void anOutsiderOrAUserWithoutEmployeeReadsNoIssue() {
        User outsider = withRoles(createUserIn(createSubdivision()));
        User withoutEmployee = withRoles(createUser(null));

        assertEquals(Set.of(), visibleIssueIds(outsider));
        assertEquals(Set.of(), visibleIssueIds(withoutEmployee));
    }

    @Test
    void adminStillReadsEveryIssue() {
        Set<UUID> ids = dataManager.load(DqIssue.class)
                .query("select e from umida_DqIssue e where e.id in :ids")
                .parameter("ids", List.of(ownIssue.getId(), otherIssue.getId()))
                .list().stream().map(DqIssue::getId).collect(Collectors.toSet());

        assertEquals(Set.of(ownIssue.getId(), otherIssue.getId()), ids);
    }

    @Test
    void aMemberClosesAnIssueOfTheirSubdivision() {
        User member = withRoles(createUserIn(ownSubdivision));

        systemAuthenticator.runWithUser(member.getUsername(), () -> {
            DqIssue issue = dataManager.load(DqIssue.class).id(ownIssue.getId()).one();
            issue.setClosingReason(DqIssueClosingReason.WONTFIX);
            issueService.markAsManualClosed(issue);
            dataManager.save(issue);
        });

        DqIssue closed = dataManager.load(DqIssue.class).id(ownIssue.getId()).one();
        assertEquals(DqIssueStatus.CLOSED, closed.getStatus());
        assertEquals(DqIssueClosingReason.WONTFIX, closed.getClosingReason());
    }

    @Test
    void anOutsiderCannotChangeAnIssueOfAnotherSubdivision() {
        User outsider = withRoles(createUserIn(createSubdivision()));
        // handed over from elsewhere: the outsider could not load it
        DqIssue issue = dataManager.load(DqIssue.class).id(ownIssue.getId()).one();
        issue.setClosingNotes("not mine");

        assertThrows(AccessDeniedException.class,
                () -> systemAuthenticator.runWithUser(outsider.getUsername(), () -> dataManager.save(issue)));
        assertNull(dataManager.load(DqIssue.class).id(ownIssue.getId()).one().getClosingNotes());
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private Set<UUID> visibleIssueIds(User user) {
        return systemAuthenticator.withUser(user.getUsername(), () -> dataManager.load(DqIssue.class)
                .query("select e from umida_DqIssue e where e.id in :ids")
                .parameter("ids", List.of(ownIssue.getId(), otherIssue.getId()))
                .list().stream().map(DqIssue::getId).collect(Collectors.toSet()));
    }

    private User withRoles(User user) {
        assignRole(user, DqIssueAssigneeRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(user, DqIssueOwnSubdivisionRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);
        return user;
    }

    private void assignRole(User user, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        testData.track(dataManager.save(assignment));
    }

    private DqIssue createOpenIssue(DqRule rule) {
        DqIssue issue = dataManager.create(DqIssue.class);
        issue.setRule(rule);
        issue.setStatus(DqIssueStatus.OPEN);
        return testData.track(dataManager.save(issue));
    }

    private OrgStructureSubdivision createSubdivision() {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName("DQ access subdivision " + suffix());
        return testData.track(dataManager.save(subdivision));
    }

    /** A user whose employee holds a position in the subdivision. */
    private User createUserIn(OrgStructureSubdivision subdivision) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("DQ access employee " + suffix());
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
        user.setUsername("dq-access-test-" + UUID.randomUUID());
        user.setFirstName("Dq");
        user.setLastName("Access");
        user.setEmployee(employee);
        return testData.track(dataManager.save(user));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        testData.cleanup();
    }
}
