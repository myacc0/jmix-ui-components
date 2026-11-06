package uz.kapitalbank.umida.report;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.Report;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantStatus;
import uz.kapitalbank.umida.service.report.ReportAccessChangedEvent;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;
import uz.kapitalbank.umida.service.report.ReportAccessException;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMember;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMemberChange;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rights on a report as {@link ReportAccessService} decides them — the author, the head of the business
 * owner, the members and the delegates of its list, the responsible of the domains, the holders of the
 * grants — and the operations on the access: requests, approvals, grants, revocations, the owner list.
 */
@SpringBootTest
@RecordApplicationEvents
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportAccessServiceTests {

    @Autowired
    ApplicationEvents events;

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    SystemAuthenticator systemAuthenticator;
    @Autowired
    ReportAccessService accessService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private ReportTestData data;
    private OrgStructureSubdivision owner;
    private ReportCustom report;
    private User head;
    private User outsider;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        owner = data.createSubdivision(true);
        head = data.createUser(data.createEmployeeIn(owner, true, null));
        outsider = data.createUser(data.createEmployeeIn(data.createSubdivision(true), false, null));
        report = data.createReport(owner, null);
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
    }

    // ---------------------------------------------------------------------
    // who may run, who manages
    // ---------------------------------------------------------------------

    @Test
    void theAuthorRunsAndManagesEveryReport() {
        ReportCustom ownerless = data.createReport(null, null);

        assertTrue(accessService.isAuthor(), "admin has full access");
        assertTrue(accessService.canRun(ownerless));
        assertTrue(accessService.canManageAccess(ownerless));
    }

    @Test
    void theHeadOfTheOwnerRunsAndManages() {
        assertTrue(as(head, () -> accessService.canRun(report)));
        assertTrue(as(head, () -> accessService.canManageAccess(report)));
        assertFalse(as(head, accessService::isAuthor));
        assertEquals(Set.of(report.getId()), as(head, accessService::personallyManagedReportIds));
    }

    @Test
    void anOutsiderOrAUserWithoutEmployeeNeitherRunsNorManages() {
        User withoutEmployee = data.createUser(null);

        assertFalse(as(outsider, () -> accessService.canRun(report)));
        assertFalse(as(outsider, () -> accessService.canManageAccess(report)));
        assertFalse(as(withoutEmployee, () -> accessService.canRun(report)));
    }

    @Test
    void anOrdinaryEmployeeOfTheOwnerNeedsToBeOnTheList() {
        OrgStructureEmployee employee = data.createEmployeeIn(owner, false, null);
        User member = data.createUser(employee);
        assertFalse(as(member, () -> accessService.canRun(report)), "being in the owner subdivision is not enough");

        accessService.saveOwnerMembers(report, List.of(new OwnerMember(employee, false)));

        assertTrue(as(member, () -> accessService.canRun(report)));
        assertFalse(as(member, () -> accessService.canManageAccess(report)));
    }

    @Test
    void aDelegateRunsAndManages() {
        OrgStructureEmployee employee = data.createEmployeeIn(owner, false, null);
        User delegate = data.createUser(employee);

        accessService.saveOwnerMembers(report, List.of(new OwnerMember(employee, true)));

        assertTrue(as(delegate, () -> accessService.canRun(report)));
        assertTrue(as(delegate, () -> accessService.canManageAccess(report)));
    }

    @Test
    void theResponsibleOfAPrimaryOrSecondaryDomainRunButDoNotManage() {
        OrgStructureSubdivision domainOwner = data.createSubdivision(true);
        User domainHead = data.createUser(data.createEmployeeIn(domainOwner, true, null));
        DictDataDomain primary = data.createDomain(domainOwner, null);
        DictDataDomain secondary = data.createDomain(data.createSubdivision(true), null);
        OrgStructureEmployee stewardEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        User steward = data.createUser(stewardEmployee);
        data.createSteward(secondary, stewardEmployee, LocalDate.now().minusDays(10), null);

        ReportCustom finalReport = data.createReport(owner, primary, secondary);

        assertTrue(as(domainHead, () -> accessService.canRun(finalReport)), "head of the primary domain owner");
        assertTrue(as(steward, () -> accessService.canRun(finalReport)), "steward of a secondary domain");
        assertFalse(as(domainHead, () -> accessService.canManageAccess(finalReport)));
        assertFalse(as(steward, () -> accessService.canManageAccess(finalReport)));
    }

    @Test
    void theOwnerSideAndTheDomainsHaveTheirOwnAccessTheGrantsAndTheAuthorsNot() {
        OrgStructureEmployee memberEmployee = data.createEmployeeIn(owner, false, null);
        User member = data.createUser(memberEmployee);
        accessService.saveOwnerMembers(report, List.of(new OwnerMember(memberEmployee, false)));

        OrgStructureSubdivision domainOwner = data.createSubdivision(true);
        User domainHead = data.createUser(data.createEmployeeIn(domainOwner, true, null));
        DictDataDomain domain = data.createDomain(domainOwner, null);
        ReportCustom domainReport = data.createReport(data.createSubdivision(true), domain);

        OrgStructureEmployee granteeEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        User grantee = data.createUser(granteeEmployee);
        accessService.grant(report, List.of(granteeEmployee), ReportAccessGrantSource.DIRECT, null);

        List<UUID> ids = List.of(report.getId(), domainReport.getId());
        assertEquals(Set.of(report.getId()), as(head, () -> accessService.ownerOrDomainAccessAmong(ids)), "the head");
        assertEquals(Set.of(report.getId()), as(member, () -> accessService.ownerOrDomainAccessAmong(ids)),
                "a member of the owner list");
        assertEquals(Set.of(domainReport.getId()), as(domainHead, () -> accessService.ownerOrDomainAccessAmong(ids)),
                "the responsible of the domain");
        assertEquals(Set.of(), as(grantee, () -> accessService.ownerOrDomainAccessAmong(ids)),
                "a direct grant runs, yet is no access of the owner");
        assertEquals(Set.of(), accessService.ownerOrDomainAccessAmong(ids), "the author asks for nothing anyway");
    }

    @Test
    void aStewardOutOfOfficeOrOfTheParentDomainDoesNotRun() {
        DictDataDomain parent = data.createDomain(data.createSubdivision(true), null);
        DictDataDomain child = data.createDomain(data.createSubdivision(true), parent);
        OrgStructureEmployee parentStewardEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        data.createSteward(parent, parentStewardEmployee, LocalDate.now().minusDays(10), null);
        OrgStructureEmployee formerEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        data.createSteward(child, formerEmployee, LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        ReportCustom childReport = data.createReport(owner, child);

        assertFalse(as(data.createUser(parentStewardEmployee), () -> accessService.canRun(childReport)));
        assertFalse(as(data.createUser(formerEmployee), () -> accessService.canRun(childReport)));
    }

    @Test
    void anExpiredRevokedOrDismissedGrantGivesNothing() {
        OrgStructureEmployee expiredEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        ReportAccessGrant expired = createGrant(expiredEmployee);
        expired.setValidFrom(LocalDate.now().minusDays(10));
        expired.setValidTo(LocalDate.now().minusDays(1));
        dataManager.save(expired);

        OrgStructureEmployee revokedEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        accessService.revoke(List.of(createGrant(revokedEmployee)), "test");

        OrgStructureEmployee dismissedEmployee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        createGrant(dismissedEmployee);
        dismissedEmployee = dataManager.load(OrgStructureEmployee.class).id(dismissedEmployee.getId()).one();
        dismissedEmployee.setDismissalDate(LocalDate.now());
        dataManager.save(dismissedEmployee);

        assertFalse(as(data.createUser(expiredEmployee), () -> accessService.canRun(report)));
        assertFalse(as(data.createUser(revokedEmployee), () -> accessService.canRun(report)));
        assertFalse(as(data.createUser(dismissedEmployee), () -> accessService.canRun(report)));
    }

    @Test
    void runnableAmongPicksTheReportsOfEachRight() {
        ReportCustom granted = data.createReport(data.createSubdivision(true), null);
        ReportCustom other = data.createReport(data.createSubdivision(true), null);
        OrgStructureEmployee employee = data.createEmployeeIn(owner, true, null);
        accessService.grant(granted, List.of(employee), ReportAccessGrantSource.DIRECT, null);

        Set<UUID> runnable = as(data.createUser(employee), () -> accessService.runnableAmong(
                List.of(report.getId(), granted.getId(), other.getId())));

        assertEquals(Set.of(report.getId(), granted.getId()), runnable);
    }

    // ---------------------------------------------------------------------
    // requests
    // ---------------------------------------------------------------------

    @Test
    void anApprovedRequestGivesAGrantUntilTheChosenDay() {
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(report, "for the audit"));
        assertEquals(ReportAccessRequestStatus.PENDING, request.getStatus());
        assertThrows(ReportAccessException.class,
                () -> as(outsider, () -> accessService.requestAccess(report, "again")), "one pending request");
        assertEquals(1, as(head, accessService::countPendingRequests));

        LocalDate validTo = LocalDate.now().plusMonths(1);
        ReportAccessRequest approved = as(head, () -> accessService.approve(request, validTo, "ok"));

        assertEquals(ReportAccessRequestStatus.APPROVED, approved.getStatus());
        assertEquals(head.getUsername(), approved.getDecidedBy());
        assertNotNull(approved.getDecidedAt());
        assertTrue(as(outsider, () -> accessService.canRun(report)));
        assertEquals(0, as(head, accessService::countPendingRequests));

        ReportAccessGrant grant = dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.request.id = :requestId")
                .parameter("requestId", request.getId())
                .one();
        assertEquals(ReportAccessGrantSource.REQUEST, grant.getSource());
        assertEquals(validTo, grant.getValidTo());
        assertFalse(grant.getCanManage());

        assertThrows(ReportAccessException.class,
                () -> as(head, () -> accessService.approve(request, null, null)), "decided already");
        assertThrows(ReportAccessException.class,
                () -> as(outsider, () -> accessService.requestAccess(report, "once more")), "has access now");
    }

    @Test
    void theRequestAndItsGrantKeepThePositionOfTheEmployeeAHeadPositionFirst() {
        OrgStructureSubdivision headed = data.createSubdivision(true);
        OrgStructureEmployee employee = data.createEmployeeIn(data.createSubdivision(true), false, null);
        data.assign(employee, headed, true);
        User user = data.createUser(employee);

        ReportAccessRequest request = as(user, () -> accessService.requestAccess(report, "for the work"));
        as(head, () -> accessService.approve(request, null, null));

        ReportAccessRequest saved = dataManager.load(ReportAccessRequest.class)
                .id(request.getId())
                .fetchPlan(fp -> fp.add("position", position -> position.add("subdivision")))
                .one();
        assertEquals(headed.getId(), saved.getPosition().getSubdivision().getId(), "the head position");
        ReportAccessGrant grant = dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.request.id = :requestId")
                .parameter("requestId", request.getId())
                .fetchPlan(fp -> fp.add("position", position -> position.add("subdivision")))
                .one();
        assertEquals(saved.getPosition().getId(), grant.getPosition().getId());

        OrgStructureEmployee withoutPosition = data.createEmployee(null);
        ReportAccessService.GrantResult result = as(head, () -> accessService.grant(report,
                List.of(withoutPosition), ReportAccessGrantSource.DIRECT, null));
        assertNull(result.created().getFirst().getPosition(), "no position in force, none kept");
    }

    @Test
    void theNamesOfTheUsersAreThoseOfTheirEmployees() {
        OrgStructureEmployee employee = data.createEmployee(null);
        User withEmployee = data.createUser(employee);
        User withoutEmployee = data.createUser(null);

        Map<String, String> names = accessService.userNames(
                Arrays.asList(withEmployee.getUsername(), withoutEmployee.getUsername(), "no-such-user", null));

        assertEquals(employee.getFullName(), names.get(withEmployee.getUsername()), "the name of the employee");
        assertEquals("Report Test", names.get(withoutEmployee.getUsername()), "the name of the user");
        assertFalse(names.containsKey("no-such-user"), "no user, no name");
    }

    @Test
    void theRequesterIsShownWithTheJobTitleAndTheSubdivisionOfThePosition() {
        OrgStructureEmployee employee = data.createEmployeeIn(owner, false, null);

        ReportAccessService.Requester requester = as(data.createUser(employee),
                () -> accessService.currentRequester().orElseThrow());

        assertEquals(employee.getId(), requester.employee().getId());
        assertEquals(owner.getName(), requester.position().getSubdivision().getName(), "the subdivision is loaded");
        assertTrue(as(data.createUser(null), () -> accessService.currentRequester().isEmpty()),
                "no employee, no requester");
    }

    @Test
    void aRejectedOrCancelledRequestGivesNothing() {
        ReportAccessRequest first = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        assertEquals(ReportAccessRequestStatus.REJECTED,
                as(head, () -> accessService.reject(first, "no need")).getStatus());
        assertFalse(as(outsider, () -> accessService.canRun(report)));

        ReportAccessRequest second = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        assertThrows(AccessDeniedException.class,
                () -> as(head, () -> accessService.cancelRequest(second)), "only the requester cancels");
        assertEquals(ReportAccessRequestStatus.CANCELLED,
                as(outsider, () -> accessService.cancelRequest(second)).getStatus());
    }

    @Test
    void aRequestNeedsAReasonAndARejectionAComment() {
        assertThrows(ReportAccessException.class,
                () -> as(outsider, () -> accessService.requestAccess(report, null)), "no reason");
        assertThrows(ReportAccessException.class,
                () -> as(outsider, () -> accessService.requestAccess(report, "  ")), "a blank reason");
        assertEquals(0, as(head, accessService::countPendingRequests), "nothing was sent");

        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(report, " for the audit "));
        assertEquals("for the audit", request.getReason());
        assertThrows(ReportAccessException.class,
                () -> as(head, () -> accessService.reject(request, null)), "no comment");
        assertThrows(ReportAccessException.class,
                () -> as(head, () -> accessService.reject(request, " ")), "a blank comment");
        assertEquals(ReportAccessRequestStatus.PENDING,
                dataManager.load(ReportAccessRequest.class).id(request.getId()).one().getStatus());

        ReportAccessRequest rejected = as(head, () -> accessService.reject(request, "not needed"));
        assertEquals(ReportAccessRequestStatus.REJECTED, rejected.getStatus());
        assertEquals("not needed", rejected.getDecisionComment());
    }

    @Test
    void theGrantOfARequestIsFoundForItsRequesterOnly() {
        ReportAccessRequest rejected = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        as(head, () -> accessService.reject(rejected, "not now"));
        ReportAccessRequest approved = as(outsider, () -> accessService.requestAccess(report, "for the audit"));
        as(head, () -> accessService.approve(approved, null, "ok"));
        List<UUID> ids = List.of(rejected.getId(), approved.getId());

        Map<UUID, ReportAccessGrant> grants = as(outsider, () -> accessService.grantsOfOwnRequests(ids));

        assertEquals(Set.of(approved.getId()), grants.keySet(), "only the approved request has a grant");
        ReportAccessGrant grant = grants.get(approved.getId());
        assertEquals(ReportAccessGrantSource.REQUEST, grant.getSource());
        assertEquals(ReportAccessGrantStatus.ACTIVE, accessService.grantStatus(grant), "the employee is loaded");
        assertTrue(as(head, () -> accessService.grantsOfOwnRequests(ids)).isEmpty(), "not the head's requests");
    }

    @Test
    void someoneWhoDoesNotManageCannotDecide() {
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        User stranger = data.createUser(data.createEmployeeIn(data.createSubdivision(true), true, null));

        assertThrows(AccessDeniedException.class, () -> as(stranger, () -> accessService.approve(request, null, null)));
        assertThrows(AccessDeniedException.class, () -> as(stranger, () -> accessService.reject(request, "not needed")));
    }

    @Test
    void aRequestChangedSinceLoadingIsNotDecided() {
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        ReportAccessRequest stale = dataManager.load(ReportAccessRequest.class).id(request.getId()).one();
        ReportAccessRequest changed = dataManager.load(ReportAccessRequest.class).id(request.getId()).one();
        changed.setReason("edited");
        dataManager.save(changed);

        assertThrows(ReportAccessException.class, () -> as(head, () -> accessService.approve(stale, null, null)));
    }

    // ---------------------------------------------------------------------
    // routing to the authors
    // ---------------------------------------------------------------------

    @Test
    void theRequestsGoToTheAuthorsWhenNoOneElseDecides() {
        ReportCustom ownerless = data.createReport(null, null);
        OrgStructureSubdivision disbanded = data.createSubdivision(false);
        User headOfDisbanded = data.createUser(data.createEmployeeIn(disbanded, true, null));
        ReportCustom ofDisbanded = data.createReport(disbanded, null);
        OrgStructureSubdivision headless = data.createSubdivision(true);
        ReportCustom ofHeadless = data.createReport(headless, null);
        ReportCustom ofHeadlessWithDelegate = data.createReport(headless, null);
        OrgStructureEmployee delegate = data.createEmployeeIn(headless, false, null);
        accessService.saveOwnerMembers(ofHeadlessWithDelegate, List.of(new OwnerMember(delegate, true)));

        Set<UUID> routed = accessService.routedToAuthorsAmong(List.of(report.getId(), ownerless.getId(),
                ofDisbanded.getId(), ofHeadless.getId(), ofHeadlessWithDelegate.getId()));

        assertEquals(Set.of(ownerless.getId(), ofDisbanded.getId(), ofHeadless.getId()), routed);
        assertFalse(as(headOfDisbanded, () -> accessService.canManageAccess(ofDisbanded)),
                "a disbanded owner manages nothing");
        assertTrue(as(data.createUser(delegate), () -> accessService.canManageAccess(ofHeadlessWithDelegate)));
    }

    @Test
    void theAuthorCountsTheRequestsGoingToTheAuthors() {
        ReportCustom ownerless = data.createReport(null, null);
        long before = accessService.countPendingRequests();

        as(outsider, () -> accessService.requestAccess(ownerless, "for the work"));
        as(outsider, () -> accessService.requestAccess(report, "for the work"));

        assertEquals(before + 1, accessService.countPendingRequests(), "the report with a head is not counted");
        assertEquals(1, as(head, accessService::countPendingRequests));
    }

    // ---------------------------------------------------------------------
    // grants and revocations
    // ---------------------------------------------------------------------

    @Test
    void aGrantToSeveralEmployeesPassesOverTheDismissedAndThoseWithAccess() {
        OrgStructureSubdivision subdivision = data.createSubdivision(true);
        OrgStructureEmployee fresh = data.createEmployeeIn(subdivision, false, null);
        OrgStructureEmployee withGrant = data.createEmployeeIn(subdivision, false, null);
        OrgStructureEmployee dismissed = data.createEmployeeIn(subdivision, false, LocalDate.now().minusDays(1));
        as(head, () -> accessService.grant(report, List.of(withGrant), ReportAccessGrantSource.DIRECT, null));

        ReportAccessService.GrantResult result = as(head, () -> accessService.grant(report,
                List.of(fresh, withGrant, dismissed), ReportAccessGrantSource.SUBDIVISION, null));

        assertEquals(1, result.created().size());
        assertEquals(fresh.getId(), result.created().getFirst().getEmployee().getId());
        assertEquals(ReportAccessGrantSource.SUBDIVISION, result.created().getFirst().getSource());
        assertEquals(2, result.skipped());
    }

    @Test
    void someoneWhoDoesNotManageCannotGrant() {
        OrgStructureEmployee employee = data.createEmployeeIn(data.createSubdivision(true), false, null);

        assertThrows(AccessDeniedException.class, () -> as(outsider,
                () -> accessService.grant(report, List.of(employee), ReportAccessGrantSource.DIRECT, null)));
        assertThrows(ReportAccessException.class, () -> accessService.grant(report, List.of(employee),
                ReportAccessGrantSource.DIRECT, LocalDate.now().minusDays(1)), "an end in the past");
    }

    @Test
    void theOwnerRevokesItsGrantsButNotThoseOfTheOwnerList() {
        OrgStructureEmployee direct = data.createEmployeeIn(data.createSubdivision(true), false, null);
        OrgStructureEmployee member = data.createEmployeeIn(owner, false, null);
        ReportAccessGrant directGrant = as(head, () -> accessService.grant(report, List.of(direct),
                ReportAccessGrantSource.DIRECT, null)).created().getFirst();
        accessService.saveOwnerMembers(report, List.of(new OwnerMember(member, false)));
        ReportAccessGrant memberGrant = accessService.ownerMembers(report).getFirst();

        ReportAccessService.RevokeResult result = as(head,
                () -> accessService.revoke(List.of(directGrant, memberGrant), "not needed"));

        assertEquals(new ReportAccessService.RevokeResult(1, 1), result);
        ReportAccessGrant revoked = dataManager.load(ReportAccessGrant.class).id(directGrant.getId()).one();
        assertEquals(head.getUsername(), revoked.getRevokedBy());
        assertEquals("not needed", revoked.getRevokeReason());
        assertFalse(as(data.createUser(direct), () -> accessService.canRun(report)));
        assertTrue(as(data.createUser(member), () -> accessService.canRun(report)), "the owner list is the authors'");

        assertThrows(AccessDeniedException.class,
                () -> as(outsider, () -> accessService.revoke(List.of(memberGrant), null)));
        assertEquals(new ReportAccessService.RevokeResult(1, 0),
                accessService.revoke(List.of(memberGrant), null), "the author revokes it");
    }

    // ---------------------------------------------------------------------
    // the owner list
    // ---------------------------------------------------------------------

    @Test
    void theAuthorKeepsTheOwnerListAndTheJournalShowsTheChanges() {
        OrgStructureEmployee first = data.createEmployeeIn(owner, false, null);
        OrgStructureEmployee second = data.createEmployeeIn(owner, false, null);

        accessService.saveOwnerMembers(report, List.of(new OwnerMember(first, false), new OwnerMember(second, false)));
        UUID unchangedGrantId = grantOf(first).getId();

        accessService.saveOwnerMembers(report, List.of(new OwnerMember(first, false), new OwnerMember(second, true)));
        assertEquals(unchangedGrantId, grantOf(first).getId(), "an unchanged member keeps the grant");
        assertTrue(grantOf(second).getCanManage());

        accessService.saveOwnerMembers(report, List.of(new OwnerMember(second, true)));
        assertEquals(List.of(second.getId()),
                accessService.ownerMembers(report).stream().map(g -> g.getEmployee().getId()).toList());

        List<ReportAccessGrant> journal = dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.report.id = :reportId")
                .parameter("reportId", report.getId())
                .list();
        assertEquals(3, journal.size(), "first, second as a member, second as a delegate");
        assertEquals(2, journal.stream().filter(g -> g.getRevokedAt() != null).count());
    }

    @Test
    void onlyTheAuthorKeepsTheOwnerListAndOnlyForAReportWithAnOwner() {
        OrgStructureEmployee employee = data.createEmployeeIn(owner, false, null);

        assertThrows(AccessDeniedException.class, () -> as(head,
                () -> {
                    accessService.saveOwnerMembers(report, List.of(new OwnerMember(employee, false)));
                    return null;
                }));
        assertThrows(ReportAccessException.class, () -> accessService.saveOwnerMembers(data.createReport(null, null),
                List.of(new OwnerMember(employee, false))));
    }

    @Test
    void theHeadAndTheDelegatesChangeTheOwnerListRowByRowButNotTheirOwnRows() {
        OrgStructureEmployee member = data.createEmployeeIn(owner, false, null);
        OrgStructureEmployee delegateEmployee = data.createEmployeeIn(owner, false, null);
        OrgStructureEmployee newcomer = data.createEmployeeIn(owner, false, null);
        accessService.saveOwnerMembers(report, List.of(new OwnerMember(member, false),
                new OwnerMember(delegateEmployee, true)));
        User delegate = data.createUser(delegateEmployee);
        UUID memberGrantId = grantOf(member).getId();

        as(head, () -> {
            accessService.changeOwnerMembers(report, List.of(new OwnerMemberChange(newcomer, true, false)));
            return null;
        });
        assertEquals(head.getUsername(), grantOf(newcomer).getCreatedBy(), "the head adds a member");
        assertEquals(memberGrantId, grantOf(member).getId(), "a row not changed keeps its grant");

        as(delegate, () -> {
            accessService.changeOwnerMembers(report, List.of(new OwnerMemberChange(member, false, false),
                    new OwnerMemberChange(newcomer, true, true)));
            return null;
        });
        ReportAccessGrant revoked = dataManager.load(ReportAccessGrant.class).id(memberGrantId).one();
        assertEquals(delegate.getUsername(), revoked.getRevokedBy(), "the delegate removes a member");
        assertTrue(grantOf(newcomer).getCanManage(), "and makes another one a delegate");
        assertEquals(Set.of(delegateEmployee.getId(), newcomer.getId()), ownerMemberIds());

        assertThrows(AccessDeniedException.class, () -> as(delegate, () -> {
            accessService.changeOwnerMembers(report, List.of(new OwnerMemberChange(delegateEmployee, false, false)));
            return null;
        }), "a delegate does not change the own row");
        assertThrows(AccessDeniedException.class, () -> as(outsider, () -> {
            accessService.changeOwnerMembers(report, List.of(new OwnerMemberChange(newcomer, false, false)));
            return null;
        }));
        assertTrue(grantOf(delegateEmployee).getCanManage());

        accessService.changeOwnerMembers(report, List.of(new OwnerMemberChange(delegateEmployee, true, false)));
        assertFalse(grantOf(delegateEmployee).getCanManage(), "the author changes every row");
        assertFalse(as(delegate, () -> accessService.canManageAccess(report)));
        assertTrue(as(delegate, () -> accessService.canRun(report)));
    }

    @Test
    void theOwnerListsAreKeptForTheReportsWithAnOwnerByTheCode() {
        ReportCustom second = data.createReport(owner, null);
        ReportCustom withoutOwner = data.createReport(null, null);
        jdbcTemplate.update("update REPORT_REPORT set CODE = ? where ID = ?", "ZZ-" + UUID.randomUUID(), report.getId());
        jdbcTemplate.update("update REPORT_REPORT set CODE = ? where ID = ?", "AA-" + UUID.randomUUID(), second.getId());

        assertEquals(List.of(second.getId(), report.getId()),
                as(head, () -> accessService.ownerListManagedReports()).stream().map(ReportCustom::getId).toList());
        assertEquals(List.of(), as(outsider, () -> accessService.ownerListManagedReports()));
        List<UUID> ofAuthor = accessService.ownerListManagedReports().stream().map(ReportCustom::getId).toList();
        assertTrue(ofAuthor.containsAll(List.of(report.getId(), second.getId())));
        assertFalse(ofAuthor.contains(withoutOwner.getId()), "no owner, no list to keep");
    }

    @Test
    void theOwnerSurvivesTheSerializedStructureOfTheReport() {
        ReportCustom reloaded = dataManager.load(ReportCustom.class).id(report.getId()).fetchPlan("report.edit").one();
        assertEquals(owner.getId(), reloaded.getBusinessOwner().getId());

        Report restored = reportsSerialization.convertToReport(reloaded.getXml());
        assertEquals(owner.getId(), assertInstanceOf(ReportCustom.class, restored).getBusinessOwner().getId());
    }

    // ---------------------------------------------------------------------
    // the access management
    // ---------------------------------------------------------------------

    @Test
    void theAccessManagementOpensForThoseWhoManageAndListsTheirReports() {
        assertTrue(as(head, accessService::canOpenAccessManagement));
        assertEquals(List.of(report.getId()), as(head, () -> accessService.managedReports().stream()
                .map(ReportCustom::getId).toList()));
        assertFalse(as(outsider, accessService::canOpenAccessManagement));
        assertEquals(List.of(), as(outsider, accessService::managedReports));
        assertTrue(accessService.canOpenAccessManagement(), "an author");
        assertTrue(accessService.managedReports().stream().anyMatch(r -> r.getId().equals(report.getId())));
    }

    @Test
    void theJournalTellsWhereEachGrantStands() {
        ReportAccessGrant active = createGrant(data.createEmployeeIn(data.createSubdivision(true), false));
        ReportAccessGrant expired = createGrant(data.createEmployeeIn(data.createSubdivision(true), false));
        expired.setValidFrom(LocalDate.now().minusDays(5));
        expired.setValidTo(LocalDate.now().minusDays(1));
        ReportAccessGrant future = createGrant(data.createEmployeeIn(data.createSubdivision(true), false));
        future.setValidFrom(LocalDate.now().plusDays(1));
        ReportAccessGrant revoked = createGrant(data.createEmployeeIn(data.createSubdivision(true), false));
        revoked.setRevokedAt(java.time.OffsetDateTime.now());
        ReportAccessGrant dismissed = createGrant(data.createEmployeeIn(data.createSubdivision(true), false));
        dismissed.getEmployee().setDismissalDate(LocalDate.now());

        assertEquals(ReportAccessGrantStatus.ACTIVE, accessService.grantStatus(active));
        assertEquals(ReportAccessGrantStatus.EXPIRED, accessService.grantStatus(expired));
        assertEquals(ReportAccessGrantStatus.NOT_STARTED, accessService.grantStatus(future));
        assertEquals(ReportAccessGrantStatus.REVOKED, accessService.grantStatus(revoked));
        assertEquals(ReportAccessGrantStatus.DISMISSED, accessService.grantStatus(dismissed));
    }

    @Test
    void everyChangeOfTheAccessIsAnnounced() {
        long before = events.stream(ReportAccessChangedEvent.class).count();
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(report, "for the work"));
        as(head, () -> accessService.reject(request, "not needed"));
        createGrant(data.createEmployeeIn(data.createSubdivision(true), false));

        assertEquals(before + 3, events.stream(ReportAccessChangedEvent.class).count());
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private <T> T as(User user, Supplier<T> action) {
        return systemAuthenticator.withUser(user.getUsername(), action::get);
    }

    private ReportAccessGrant grantOf(OrgStructureEmployee employee) {
        return accessService.ownerMembers(report).stream()
                .filter(grant -> grant.getEmployee().getId().equals(employee.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Set<String> ownerMemberIds() {
        return accessService.ownerMembers(report).stream()
                .map(grant -> grant.getEmployee().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private ReportAccessGrant createGrant(OrgStructureEmployee employee) {
        return accessService.grant(report, List.of(employee), ReportAccessGrantSource.DIRECT, null)
                .created().getFirst();
    }
}
