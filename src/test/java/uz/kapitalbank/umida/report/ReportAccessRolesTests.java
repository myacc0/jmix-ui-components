package uz.kapitalbank.umida.report;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportExecution;
import io.jmix.reports.runner.ReportRunner;
import io.jmix.reports.yarg.reporting.ReportOutputDocument;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.security.ReportAuthorRole;
import uz.kapitalbank.umida.security.ReportViewerRowsRole;
import uz.kapitalbank.umida.security.UiMinimalRole;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMember;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import uz.kapitalbank.umida.test_support.ReportTestData;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What {@link ReportViewerRole} with {@link ReportViewerRowsRole}, and {@link ReportAuthorRole}, let a user
 * read and open: every report, the own runs and those of the managed reports, the grants and the requests
 * of the managed reports, the own requests; and that a viewer with access really runs a report.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class ReportAccessRolesTests {

    @Autowired
    DataManager dataManager;
    @Autowired
    Metadata metadata;
    @Autowired
    ReportsPersistence reportsPersistence;
    @Autowired
    ReportsSerialization reportsSerialization;
    @Autowired
    ReportRunner reportRunner;
    @Autowired
    SystemAuthenticator systemAuthenticator;
    @Autowired
    AccessManager accessManager;
    @Autowired
    ReportAccessService accessService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private ReportTestData data;

    private ReportCustom ownReport;
    private ReportCustom otherReport;
    private User head;
    private User member;
    private OrgStructureEmployee memberEmployee;
    private User outsider;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(dataManager, metadata, reportsPersistence, reportsSerialization, jdbcTemplate);
        OrgStructureSubdivision owner = data.createSubdivision(true);
        ownReport = data.createReport(owner);
        otherReport = data.createReport(data.createSubdivision(true));

        head = data.createViewer(data.createEmployeeIn(owner, true));
        memberEmployee = data.createEmployeeIn(owner, false);
        member = data.createViewer(memberEmployee);
        outsider = data.createViewer(data.createEmployeeIn(data.createSubdivision(true), false));
        accessService.saveOwnerMembers(ownReport, List.of(new OwnerMember(memberEmployee, false)));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
    }

    @Test
    void aViewerReadsEveryReportButIsNoAuthor() {
        Set<UUID> visible = as(outsider, () -> dataManager.load(ReportCustom.class)
                .query("select r from umida_ReportCustom r where r.id in :ids")
                .parameter("ids", data.reportIds())
                .list().stream().map(Report::getId).collect(Collectors.toSet()));

        assertEquals(Set.copyOf(data.reportIds()), visible);
        assertFalse(as(outsider, accessService::isAuthor));
    }

    @Test
    void aViewerOpensTheListAndTheRunDialogsButNotTheEditorNorTheRunViewOfTheAddOn() {
        assertTrue(as(outsider, () -> canShow("umida_ReportCustom.list")));
        assertTrue(as(outsider, () -> canShow("report_InputParametersDialogView")));
        assertTrue(as(outsider, () -> canShow("report_ReportExecution.list")));
        assertFalse(as(outsider, () -> canShow("umida_ReportCustom.detail")));
        assertFalse(as(outsider, () -> canShow("report_ReportRunView")), "it would bypass the access rules");
        assertFalse(as(outsider, () -> canShow("umida_ReportGroupCustom.list")));
    }

    @Test
    void anAuthorOpensTheEditorAndIsAnAuthorEvenWithTheRowsRole() {
        User author = data.createUser(null);
        data.assignRole(author, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        data.assignRole(author, ReportAuthorRole.CODE, RoleAssignmentRoleType.RESOURCE);
        data.assignRole(author, ReportViewerRowsRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(otherReport, "for the work"));

        assertTrue(as(author, accessService::isAuthor));
        assertTrue(as(author, () -> canShow("umida_ReportCustom.detail")));
        assertTrue(as(author, () -> canShow("umida_ReportGroupCustom.list")));
        assertTrue(as(author, () -> canShow("umida_ReportCustom.list")));
        assertEquals(Set.of(request.getId()), visibleRequests(author), "the rows role does not narrow an author");
        assertEquals(1, visibleGrants(author).size(), "the grant of the member");
    }

    @Test
    void aViewerSeesTheOwnRunsAndTheRunsOfTheManagedReports() {
        UUID memberRun = createExecution(ownReport, member);
        UUID outsiderRunOfOwn = createExecution(ownReport, outsider);
        UUID outsiderRunOfOther = createExecution(otherReport, outsider);

        assertEquals(Set.of(memberRun, outsiderRunOfOwn), visibleExecutions(head), "every run of the managed report");
        assertEquals(Set.of(memberRun), visibleExecutions(member), "a member sees the own runs only");
        assertEquals(Set.of(outsiderRunOfOwn, outsiderRunOfOther), visibleExecutions(outsider));
    }

    @Test
    void theGrantsAndTheRequestsAreReadByThoseWhoManageTheReport() {
        ReportAccessRequest request = as(outsider, () -> accessService.requestAccess(ownReport, "audit"));
        ReportAccessRequest otherRequest = as(member, () -> accessService.requestAccess(otherReport, "for the work"));

        assertEquals(1, visibleGrants(head).size(), "the grant of the member of the own report");
        assertEquals(Set.of(), visibleGrants(member).keySet(), "a member manages nothing");
        assertEquals(Set.of(), visibleGrants(outsider).keySet());

        assertEquals(Set.of(request.getId()), visibleRequests(head));
        assertEquals(Set.of(request.getId()), visibleRequests(outsider), "the own request");
        assertEquals(Set.of(otherRequest.getId()), visibleRequests(member), "the own request");
    }

    @Test
    void aDelegateReadsTheGrantsAndTheRunsOfTheReport() {
        OrgStructureEmployee delegateEmployee = data.createEmployeeIn(data.createSubdivision(true), false);
        User delegate = data.createViewer(delegateEmployee);
        accessService.saveOwnerMembers(ownReport, List.of(
                new OwnerMember(memberEmployee, false), new OwnerMember(delegateEmployee, true)));
        UUID memberRun = createExecution(ownReport, member);

        assertEquals(2, visibleGrants(delegate).size());
        assertEquals(Set.of(memberRun), visibleExecutions(delegate));
    }

    @Test
    void aViewerWithAccessRunsTheReportAndTheRunIsRecorded() {
        assertTrue(as(member, () -> accessService.canRun(ownReport)));

        ReportOutputDocument document = as(member, () -> reportRunner.byReportEntity(ownReport).run());

        assertEquals("ok", new String(document.getContent(), StandardCharsets.UTF_8).trim());
        assertEquals(1, visibleExecutions(member).size(), "the run is in the member's history");
    }

    @Test
    void theHistoryIsOpenedByTheManagersTheMembersAndTheDomainsButNotByTheOtherGrants() {
        OrgStructureEmployee grantedEmployee = data.createEmployeeIn(data.createSubdivision(true), false);
        User granted = data.createViewer(grantedEmployee);
        accessService.grant(ownReport, List.of(grantedEmployee), ReportAccessGrantSource.DIRECT, null);

        assertTrue(as(head, () -> accessService.canViewHistory(ownReport)));
        assertTrue(as(member, () -> accessService.canViewHistory(ownReport)));
        assertTrue(as(granted, () -> accessService.canRun(ownReport)));
        assertFalse(as(granted, () -> accessService.canViewHistory(ownReport)), "a grant gives no history");
        assertFalse(as(outsider, () -> accessService.canViewHistory(ownReport)));
        assertTrue(accessService.canViewHistory(otherReport), "admin is an author");
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private <T> T as(User user, Supplier<T> action) {
        return systemAuthenticator.withUser(user.getUsername(), action::get);
    }

    private boolean canShow(String viewId) {
        UiShowViewContext context = new UiShowViewContext(viewId);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private Set<UUID> visibleExecutions(User user) {
        return as(user, () -> dataManager.load(ReportExecution.class)
                .query("select e from report_ReportExecution e where e.report.id in :ids")
                .parameter("ids", data.reportIds())
                .list().stream().map(ReportExecution::getId).collect(Collectors.toSet()));
    }

    private Map<UUID, ReportAccessGrant> visibleGrants(User user) {
        return as(user, () -> dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.report.id in :ids")
                .parameter("ids", data.reportIds())
                .list().stream().collect(Collectors.toMap(ReportAccessGrant::getId, g -> g)));
    }

    private Set<UUID> visibleRequests(User user) {
        return as(user, () -> dataManager.load(ReportAccessRequest.class)
                .query("select q from umida_ReportAccessRequest q where q.report.id in :ids")
                .parameter("ids", data.reportIds())
                .list().stream().map(ReportAccessRequest::getId).collect(Collectors.toSet()));
    }

    private UUID createExecution(ReportCustom report, User user) {
        ReportExecution execution = dataManager.create(ReportExecution.class);
        execution.setReport(report);
        execution.setReportName(report.getName());
        execution.setUsername(user.getUsername());
        execution.setStartTime(new Date());
        execution.setSuccess(true);
        return dataManager.save(execution).getId();
    }

}
