package uz.kapitalbank.umida.test_support;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.reports.ReportsPersistence;
import io.jmix.reports.ReportsSerialization;
import io.jmix.reports.entity.BandDefinition;
import io.jmix.reports.entity.Orientation;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reports.entity.ReportTemplate;
import io.jmix.security.role.assignment.RoleAssignmentRoleType;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.dict.DictDataDomainSteward;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.security.ReportViewerRole;
import uz.kapitalbank.umida.security.ReportViewerRowsRole;
import uz.kapitalbank.umida.security.UiMinimalRole;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The data a test of the access to the reports stands on: subdivisions, employees holding positions in them,
 * their users with roles, domains with stewards, and reports the way the editor of the add-on saves them,
 * with an HTML template printing "ok" that runs without any data set. Everything is removed for real by
 * {@link #cleanup()}, the runs of the reports and of the users included; the grants and the requests go
 * with their reports and employees.
 */
public class ReportTestData {

    private final DataManager dataManager;
    private final Metadata metadata;
    private final ReportsPersistence reportsPersistence;
    private final ReportsSerialization reportsSerialization;
    private final JdbcTemplate jdbcTemplate;
    private final DqTestData tracker;
    private final List<UUID> reportIds = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();

    public ReportTestData(DataManager dataManager, Metadata metadata, ReportsPersistence reportsPersistence,
                          ReportsSerialization reportsSerialization, JdbcTemplate jdbcTemplate) {
        this.dataManager = dataManager;
        this.metadata = metadata;
        this.reportsPersistence = reportsPersistence;
        this.reportsSerialization = reportsSerialization;
        this.jdbcTemplate = jdbcTemplate;
        this.tracker = new DqTestData(dataManager);
    }

    public List<UUID> reportIds() {
        return reportIds;
    }

    public OrgStructureSubdivision createSubdivision(boolean active) {
        return createSubdivision(null, active);
    }

    public OrgStructureSubdivision createSubdivision(@Nullable OrgStructureSubdivision parent, boolean active) {
        OrgStructureSubdivision subdivision = dataManager.create(OrgStructureSubdivision.class);
        subdivision.setId(UUID.randomUUID().toString());
        subdivision.setName("Report test subdivision " + suffix());
        subdivision.setParent(parent);
        subdivision.setIsActive(active ? 1 : 0);
        return tracker.track(dataManager.save(subdivision));
    }

    public OrgStructureEmployee createEmployee(@Nullable LocalDate dismissalDate) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Report test employee " + suffix());
        employee.setDismissalDate(dismissalDate);
        return tracker.track(dataManager.save(employee));
    }

    /** An employee currently assigned to a position of the subdivision, its head position if {@code head}. */
    public OrgStructureEmployee createEmployeeIn(OrgStructureSubdivision subdivision, boolean head,
                                                 @Nullable LocalDate dismissalDate) {
        OrgStructureEmployee employee = createEmployee(dismissalDate);
        assign(employee, subdivision, head);
        return employee;
    }

    public OrgStructureEmployee createEmployeeIn(OrgStructureSubdivision subdivision, boolean head) {
        return createEmployeeIn(subdivision, head, null);
    }

    /** A current assignment of the employee to a new active position of the subdivision. */
    public void assign(OrgStructureEmployee employee, OrgStructureSubdivision subdivision, boolean head) {
        OrgStructurePosition position = dataManager.create(OrgStructurePosition.class);
        position.setId(UUID.randomUUID().toString());
        position.setSubdivision(subdivision);
        position.setIsheadofsubdivision(head ? 1 : 0);
        position = tracker.track(dataManager.save(position));

        OrgStructureAssignments assignment = dataManager.create(OrgStructureAssignments.class);
        assignment.setId(UUID.randomUUID().toString());
        assignment.setPosition(position);
        assignment.setEmployee(employee);
        assignment.setStartDate(LocalDate.now().minusYears(1));
        tracker.track(dataManager.save(assignment));
    }

    /** A user of the employee, or of no employee, without roles. */
    public User createUser(@Nullable OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        // short: the runs of the reports keep the username in 50 characters
        user.setUsername("report-test-" + suffix());
        user.setFirstName("Report");
        user.setLastName("Test");
        user.setEmployee(employee);
        usernames.add(user.getUsername());
        return tracker.track(dataManager.save(user));
    }

    /** A user of the employee with the roles every non-author gets. */
    public User createViewer(@Nullable OrgStructureEmployee employee) {
        User user = createUser(employee);
        assignRole(user, UiMinimalRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(user, ReportViewerRole.CODE, RoleAssignmentRoleType.RESOURCE);
        assignRole(user, ReportViewerRowsRole.CODE, RoleAssignmentRoleType.ROW_LEVEL);
        return user;
    }

    public void assignRole(User user, String roleCode, String roleType) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(user.getUsername());
        assignment.setRoleCode(roleCode);
        assignment.setRoleType(roleType);
        tracker.track(dataManager.save(assignment));
    }

    public DictDataDomain createDomain(OrgStructureSubdivision domainOwner, @Nullable DictDataDomain parent) {
        DictDataDomain domain = dataManager.create(DictDataDomain.class);
        domain.setCode("report-test-" + suffix());
        domain.setShortName("Report test domain " + suffix());
        domain.setBusinessOwner(domainOwner);
        domain.setParent(parent);
        domain.setAssignDate(LocalDate.now());
        return tracker.track(dataManager.save(domain));
    }

    public void createSteward(DictDataDomain domain, OrgStructureEmployee employee, LocalDate begin,
                              @Nullable LocalDate end) {
        DictDataDomainSteward steward = dataManager.create(DictDataDomainSteward.class);
        steward.setDomain(domain);
        steward.setEmployee(employee);
        steward.setDateBegin(begin);
        steward.setDateEnd(end);
        tracker.track(dataManager.save(steward));
    }

    public ReportCustom createReport(@Nullable OrgStructureSubdivision businessOwner) {
        return createReport(businessOwner, null);
    }

    public ReportCustom createReport(@Nullable OrgStructureSubdivision businessOwner, @Nullable DictDataDomain domain,
                                     DictDataDomain... secondaryDomains) {
        ReportCustom report = (ReportCustom) metadata.create(Report.class);
        report.setName("Report test " + UUID.randomUUID());
        report.setBusinessOwner(businessOwner);
        report.setDomain(domain);
        report.setSecondaryDomains(new ArrayList<>(List.of(secondaryDomains)));

        BandDefinition root = metadata.create(BandDefinition.class);
        root.setName("Root");
        root.setPosition(0);
        root.setOrientation(Orientation.HORIZONTAL);
        root.setReport(report);
        report.getBands().add(root);

        ReportTemplate template = metadata.create(ReportTemplate.class);
        template.setReport(report);
        template.setCode(ReportTemplate.DEFAULT_TEMPLATE_CODE);
        template.setName("template.html");
        template.setReportOutputType(ReportOutputType.HTML);
        template.setContent("ok".getBytes(StandardCharsets.UTF_8));
        report.setTemplates(new ArrayList<>(List.of(template)));
        report.setDefaultTemplate(template);
        report.setXml(reportsSerialization.convertToString(report));
        ReportCustom saved = (ReportCustom) reportsPersistence.save(report);
        reportIds.add(saved.getId());
        return tracker.track(saved);
    }

    public void cleanup() {
        usernames.forEach(username -> jdbcTemplate.update("delete from REPORT_EXECUTION where USERNAME = ?", username));
        reportIds.forEach(id -> {
            jdbcTemplate.update("delete from REPORT_EXECUTION where REPORT_ID = ?", id);
            // the links to the domains, which a removal of the report leaves behind
            jdbcTemplate.update("delete from UMIDA_REPORT_SECONDARY_DOMAIN_LINK where REPORT_ID = ?", id);
        });
        usernames.clear();
        reportIds.clear();
        tracker.cleanup();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
