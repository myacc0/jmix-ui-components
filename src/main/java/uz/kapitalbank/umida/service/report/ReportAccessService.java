package uz.kapitalbank.umida.service.report;

import io.jmix.core.AccessManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.core.FluentValuesLoader;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.reports.entity.Report;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportAccessRequest;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantStatus;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.TODAY_PARAM;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.active;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.currentAssignment;

/**
 * Who may run a report and who manages the access to it, and the operations on that access: the requests
 * of the employees, their approval or rejection, the grants given without a request, the revocations and
 * the list of the business owner.
 *
 * <p>The roles of the users decide one thing only: whether the user is an <em>author</em>, who may change
 * the reports ({@link uz.kapitalbank.umida.security.ReportAuthorRole}, {@code reports-full-access} of the add-on, or full access). An author runs every
 * report and manages the access to every one of them. For everyone else the rights come from the data, for
 * the employee linked to the user, as long as the employee is not dismissed:
 * <ul>
 *     <li>the <b>head</b> of the business owner of the report ({@code ReportCustom.businessOwner}), an
 *     employee currently assigned to its active head position, runs the report and manages the access;</li>
 *     <li>an employee with a grant in force ({@link ReportAccessGrant}) runs the report, and with
 *     {@code canManage} — a <b>delegate</b> — manages the access too;</li>
 *     <li>the <b>responsible of a domain</b> of the report, its primary or one of its secondary domains, runs
 *     the report: the head of the subdivision owning the domain and the stewards of the domain in office.
 *     The parent domains give nothing on the reports of their children.</li>
 * </ul>
 * The head and the delegates manage the access only while the owner subdivision is active. A request for a
 * report whose owner is missing or disbanded, or which has neither a head nor a delegate, goes to the
 * authors.
 *
 * <p>The list of the business owner — the employees running the report on behalf of the owner, the
 * delegates among them — is kept by the authors alone, as grants of the source {@code OWNER}: the head and
 * the delegates neither change nor revoke them.
 *
 * <p>Reads and writes unconstrained: the decision is made here, whatever the roles let the user read, and
 * every operation checks the rights of the current user first.
 */
@Service
public class ReportAccessService {

    /** A member of the list of the business owner of a report. */
    public record OwnerMember(OrgStructureEmployee employee, boolean canManage) {
    }

    /** The grants given, and how many of the employees were passed over: dismissed, or with access already. */
    public record GrantResult(List<ReportAccessGrant> created, int skipped) {
    }

    /** How many grants were revoked, and how many were passed over: revoked already, or kept by the authors. */
    public record RevokeResult(int revoked, int skipped) {
    }

    private static final String EMPLOYEE_PARAM = "employeeId";
    private static final String IDS_PARAM = "reportIds";

    private final UnconstrainedDataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final AccessManager accessManager;
    private final Metadata metadata;
    private final TimeSource timeSource;
    private final Messages messages;
    private final ApplicationEventPublisher eventPublisher;

    public ReportAccessService(UnconstrainedDataManager dataManager,
                               CurrentAuthentication currentAuthentication,
                               AccessManager accessManager,
                               Metadata metadata,
                               TimeSource timeSource,
                               Messages messages,
                               ApplicationEventPublisher eventPublisher) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.accessManager = accessManager;
        this.metadata = metadata;
        this.timeSource = timeSource;
        this.messages = messages;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------
    // who may do what
    // ---------------------------------------------------------------------

    /** Whether the current user is an author: may change the reports, so runs and manages all of them. */
    public boolean isAuthor() {
        CrudEntityContext context = new CrudEntityContext(metadata.getClass(Report.class));
        accessManager.applyRegisteredConstraints(context);
        return context.isUpdatePermitted();
    }

    /**
     * The employee linked to the current user, unless the user has none or the employee is dismissed: such a
     * user has no rights on the reports but those of the roles.
     */
    public Optional<OrgStructureEmployee> currentEmployee() {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", currentAuthentication.getUser().getUsername())
                .fetchPlan(fp -> fp.add("employee", FetchPlan.BASE))
                .optional()
                .map(User::getEmployee)
                .filter(employee -> !isDismissed(employee));
    }

    public boolean canRun(ReportCustom report) {
        return runnableAmong(List.of(report.getId())).contains(report.getId());
    }

    /** The reports among the given ones that the current user may run. */
    public Set<UUID> runnableAmong(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return Set.of();
        }
        if (isAuthor()) {
            return new HashSet<>(reportIds);
        }
        Optional<String> employeeId = currentEmployee().map(OrgStructureEmployee::getId);
        if (employeeId.isEmpty()) {
            return Set.of();
        }

        Set<UUID> runnable = new HashSet<>(headedReports(employeeId.get(), reportIds, false));
        runnable.addAll(grantedReports(employeeId.get(), reportIds, false));
        runnable.addAll(domainReports(employeeId.get(), reportIds));
        return runnable;
    }

    public boolean canViewHistory(ReportCustom report) {
        return historyViewableAmong(List.of(report.getId())).contains(report.getId());
    }

    /**
     * The reports among the given ones whose history of runs the current user may open: an author, and those
     * who manage the access, see every run of the report; the members of the list of the business owner and
     * the responsible of the domains see their own runs ({@code ReportViewerRowsRole} narrows the rows). The
     * holders of the other grants have no history.
     */
    public Set<UUID> historyViewableAmong(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return Set.of();
        }
        if (isAuthor()) {
            return new HashSet<>(reportIds);
        }
        Optional<String> employeeId = currentEmployee().map(OrgStructureEmployee::getId);
        if (employeeId.isEmpty()) {
            return Set.of();
        }

        Set<UUID> viewable = new HashSet<>(personallyManaged(reportIds));
        viewable.addAll(reportIds(dataManager.loadValues("select g.report.id from umida_ReportAccessGrant g" +
                        " where g.employee.id = :" + EMPLOYEE_PARAM + " and g.source = :source and " + inForce("g") +
                        " and g.report.id in :" + IDS_PARAM)
                .parameter(EMPLOYEE_PARAM, employeeId.get())
                .parameter("source", ReportAccessGrantSource.OWNER.getId())
                .parameter(IDS_PARAM, reportIds)
                .parameter(TODAY_PARAM, today())));
        viewable.addAll(domainReports(employeeId.get(), reportIds));
        return viewable;
    }

    public boolean canManageAccess(ReportCustom report) {
        return managedAmong(List.of(report.getId())).contains(report.getId());
    }

    /** The reports among the given ones whose access the current user manages: all of them for an author. */
    public Set<UUID> managedAmong(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return Set.of();
        }
        return isAuthor() ? new HashSet<>(reportIds) : personallyManaged(reportIds);
    }

    /**
     * The reports whose access the current user manages as the head of their owner or as a delegate. An
     * author manages all the reports besides; this does not count them.
     */
    public Set<UUID> personallyManagedReportIds() {
        return personallyManaged(null);
    }

    /** Whether the current user has anything to manage: an author, or the head or a delegate of an owner. */
    public boolean canOpenAccessManagement() {
        return isAuthor() || !personallyManagedReportIds().isEmpty();
    }

    /** The reports whose access the current user manages, by name: every report for an author. */
    public List<ReportCustom> managedReports() {
        if (isAuthor()) {
            return dataManager.load(ReportCustom.class)
                    .query("select r from umida_ReportCustom r order by r.name")
                    .fetchPlan(FetchPlan.INSTANCE_NAME)
                    .list();
        }
        Set<UUID> ids = personallyManagedReportIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        return dataManager.load(ReportCustom.class)
                .query("select r from umida_ReportCustom r where r.id in :ids order by r.name")
                .parameter("ids", ids)
                .fetchPlan(FetchPlan.INSTANCE_NAME)
                .list();
    }

    /** Where the grant stands today; the grant needs its employee loaded. */
    public ReportAccessGrantStatus grantStatus(ReportAccessGrant grant) {
        LocalDate today = today();
        if (grant.getRevokedAt() != null) {
            return ReportAccessGrantStatus.REVOKED;
        }
        if (grant.getEmployee() != null && isDismissed(grant.getEmployee())) {
            return ReportAccessGrantStatus.DISMISSED;
        }
        if (grant.getValidTo() != null && grant.getValidTo().isBefore(today)) {
            return ReportAccessGrantStatus.EXPIRED;
        }
        if (grant.getValidFrom() != null && grant.getValidFrom().isAfter(today)) {
            return ReportAccessGrantStatus.NOT_STARTED;
        }
        return ReportAccessGrantStatus.ACTIVE;
    }

    /**
     * The reports among the given ones whose requests go to the authors: without an owner, with a disbanded
     * owner, or with neither a head of the owner nor a delegate in office.
     */
    public Set<UUID> routedToAuthorsAmong(Collection<UUID> reportIds) {
        if (reportIds.isEmpty()) {
            return Set.of();
        }
        LocalDate today = today();
        Set<UUID> withHead = reportIds(dataManager.loadValues(
                        "select r.id from umida_ReportCustom r, umida_OrgStructureAssignments a" +
                                " where r.id in :" + IDS_PARAM +
                                " and a.position.subdivision = r.businessOwner" +
                                " and a.position.isheadofsubdivision = 1 and " + active("a.position") +
                                " and " + currentAssignment("a") + " and " + notDismissed("a.employee"))
                .parameter(IDS_PARAM, reportIds)
                .parameter(TODAY_PARAM, today));
        Set<UUID> withDelegate = reportIds(dataManager.loadValues(
                        "select g.report.id from umida_ReportAccessGrant g" +
                                " where g.report.id in :" + IDS_PARAM + " and g.canManage = true and " + inForce("g"))
                .parameter(IDS_PARAM, reportIds)
                .parameter(TODAY_PARAM, today));

        Set<UUID> routed = new HashSet<>();
        dataManager.loadValues("select r.id, o.id, o.isActive from umida_ReportCustom r left join r.businessOwner o" +
                        " where r.id in :" + IDS_PARAM)
                .parameter(IDS_PARAM, reportIds)
                .properties("reportId", "ownerId", "ownerActive")
                .list()
                .forEach(row -> {
                    UUID reportId = row.getValue("reportId");
                    Integer ownerActive = row.getValue("ownerActive");
                    boolean disbanded = row.getValue("ownerId") == null || Objects.equals(ownerActive, 0);
                    if (disbanded || !withHead.contains(reportId) && !withDelegate.contains(reportId)) {
                        routed.add(reportId);
                    }
                });
        return routed;
    }

    /**
     * The number of the pending requests the current user is to decide on: those for the reports managed as
     * the head or a delegate, and for an author also those going to the authors.
     */
    public long countPendingRequests() {
        Map<UUID, Long> pendingByReport = new HashMap<>();
        dataManager.loadValues("select q.report.id, count(q) from umida_ReportAccessRequest q" +
                        " where q.status = :status group by q.report.id")
                .parameter("status", ReportAccessRequestStatus.PENDING.getId())
                .properties("reportId", "count")
                .list()
                .forEach(row -> pendingByReport.put(row.getValue("reportId"), row.getValue("count")));
        if (pendingByReport.isEmpty()) {
            return 0;
        }

        Set<UUID> counted = new HashSet<>(personallyManaged(pendingByReport.keySet()));
        if (isAuthor()) {
            counted.addAll(routedToAuthorsAmong(pendingByReport.keySet()));
        }
        return counted.stream().mapToLong(pendingByReport::get).sum();
    }

    // ---------------------------------------------------------------------
    // requests
    // ---------------------------------------------------------------------

    /** The current user asks to run the report. */
    public ReportAccessRequest requestAccess(ReportCustom report, @Nullable String reason) {
        OrgStructureEmployee employee = currentEmployee()
                .orElseThrow(() -> new ReportAccessException(message("noEmployee")));
        if (canRun(report)) {
            throw new ReportAccessException(message("alreadyHasAccess"));
        }
        boolean pending = dataManager.load(ReportAccessRequest.class)
                .query("select q from umida_ReportAccessRequest q" +
                        " where q.report.id = :reportId and q.employee.id = :" + EMPLOYEE_PARAM + " and q.status = :status")
                .parameter("reportId", report.getId())
                .parameter(EMPLOYEE_PARAM, employee.getId())
                .parameter("status", ReportAccessRequestStatus.PENDING.getId())
                .fetchPlanProperties("id")
                .maxResults(1)
                .optional()
                .isPresent();
        if (pending) {
            throw new ReportAccessException(message("requestPending"));
        }

        ReportAccessRequest request = dataManager.create(ReportAccessRequest.class);
        request.setReport(dataManager.getReference(ReportCustom.class, report.getId()));
        request.setEmployee(employee);
        request.setPosition(currentPositions(Set.of(employee.getId())).get(employee.getId()));
        request.setReason(reason);
        ReportAccessRequest saved = dataManager.save(request);
        changed();
        return saved;
    }

    /** The requester withdraws a pending request of their own. */
    public ReportAccessRequest cancelRequest(ReportAccessRequest request) {
        ReportAccessRequest current = reloadPending(request);
        boolean own = currentEmployee()
                .map(employee -> employee.getId().equals(current.getEmployee().getId()))
                .orElse(false);
        if (!own) {
            throw new AccessDeniedException("report access request", String.valueOf(request.getId()), "cancel");
        }
        decide(current, ReportAccessRequestStatus.CANCELLED, null);
        ReportAccessRequest saved = dataManager.save(current);
        changed();
        return saved;
    }

    /**
     * Approves a pending request and gives the requester a grant until {@code validTo}, open when
     * {@code null} — unless the requester has a grant in force already. Fails if someone else has decided
     * on the request meanwhile.
     */
    public ReportAccessRequest approve(ReportAccessRequest request, @Nullable LocalDate validTo,
                                       @Nullable String comment) {
        ReportAccessRequest current = reloadPending(request);
        requireManaged(current.getReport());
        checkValidTo(validTo);
        decide(current, ReportAccessRequestStatus.APPROVED, comment);

        SaveContext saveContext = new SaveContext().saving(current);
        if (grantsInForce(current.getReport().getId(), Set.of(current.getEmployee().getId())).isEmpty()) {
            String employeeId = current.getEmployee().getId();
            ReportAccessGrant grant = newGrant(current.getReport(), current.getEmployee(),
                    currentPositions(Set.of(employeeId)).get(employeeId), ReportAccessGrantSource.REQUEST, false, validTo);
            grant.setRequest(current);
            saveContext.saving(grant);
        }
        ReportAccessRequest saved = dataManager.save(saveContext).get(current);
        changed();
        return saved;
    }

    /** Rejects a pending request. Fails if someone else has decided on the request meanwhile. */
    public ReportAccessRequest reject(ReportAccessRequest request, @Nullable String comment) {
        ReportAccessRequest current = reloadPending(request);
        requireManaged(current.getReport());
        decide(current, ReportAccessRequestStatus.REJECTED, comment);
        ReportAccessRequest saved = dataManager.save(current);
        changed();
        return saved;
    }

    // ---------------------------------------------------------------------
    // grants
    // ---------------------------------------------------------------------

    /**
     * Gives the employees the right to run the report without a request: one of them ({@code DIRECT}) or
     * those picked in a subdivision ({@code SUBDIVISION}). Passes over the dismissed employees and those
     * with a grant in force already.
     */
    public GrantResult grant(ReportCustom report, Collection<OrgStructureEmployee> employees,
                             ReportAccessGrantSource source, @Nullable LocalDate validTo) {
        if (source != ReportAccessGrantSource.DIRECT && source != ReportAccessGrantSource.SUBDIVISION) {
            throw new IllegalArgumentException("Only direct and subdivision grants are given here, not " + source);
        }
        requireManaged(report);
        checkValidTo(validTo);

        Set<String> requested = employees.stream().map(OrgStructureEmployee::getId).collect(Collectors.toSet());
        List<OrgStructureEmployee> eligible = loadEmployees(requested).stream()
                .filter(employee -> !isDismissed(employee))
                .toList();
        Set<String> withGrant = grantsInForce(report.getId(), requested).stream()
                .map(grant -> grant.getEmployee().getId())
                .collect(Collectors.toSet());

        Map<String, OrgStructurePosition> positions = currentPositions(
                eligible.stream().map(OrgStructureEmployee::getId).toList());
        SaveContext saveContext = new SaveContext();
        List<ReportAccessGrant> created = new ArrayList<>();
        for (OrgStructureEmployee employee : eligible) {
            if (!withGrant.contains(employee.getId())) {
                ReportAccessGrant grant = newGrant(report, employee, positions.get(employee.getId()), source, false,
                        validTo);
                saveContext.saving(grant);
                created.add(grant);
            }
        }
        if (created.isEmpty()) {
            return new GrantResult(List.of(), requested.size());
        }
        var saved = dataManager.save(saveContext);
        changed();
        return new GrantResult(created.stream().map(saved::get).toList(), requested.size() - created.size());
    }

    /**
     * Revokes the grants. Passes over those revoked already and, but for an author, those of the list of the
     * business owner, which the authors keep.
     */
    public RevokeResult revoke(Collection<ReportAccessGrant> grants, @Nullable String reason) {
        if (grants.isEmpty()) {
            return new RevokeResult(0, 0);
        }
        List<ReportAccessGrant> current = dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g where g.id in :ids")
                .parameter("ids", grants.stream().map(ReportAccessGrant::getId).toList())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("report", FetchPlan.INSTANCE_NAME))
                .list();
        Set<UUID> reportIds = current.stream().map(grant -> grant.getReport().getId()).collect(Collectors.toSet());
        Set<UUID> managed = managedAmong(reportIds);
        if (!managed.containsAll(reportIds)) {
            throw new AccessDeniedException("report access grant", "revoke");
        }

        boolean author = isAuthor();
        SaveContext saveContext = new SaveContext();
        int revoked = 0;
        for (ReportAccessGrant grant : current) {
            boolean kept = grant.getSource() == ReportAccessGrantSource.OWNER && !author;
            if (grant.getRevokedAt() == null && !kept) {
                markRevoked(grant, reason);
                saveContext.saving(grant);
                revoked++;
            }
        }
        if (revoked > 0) {
            dataManager.save(saveContext);
            changed();
        }
        return new RevokeResult(revoked, grants.size() - revoked);
    }

    /** The grants in force of the list of the business owner of the report. */
    public List<ReportAccessGrant> ownerMembers(ReportCustom report) {
        return dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g" +
                        " where g.report.id = :reportId and g.source = :source and " + inForce("g"))
                .parameter("reportId", report.getId())
                .parameter("source", ReportAccessGrantSource.OWNER.getId())
                .parameter(TODAY_PARAM, today())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee", FetchPlan.BASE))
                .list();
    }

    /**
     * Makes the list of the business owner of the report the given one; authors only. A member with an
     * unchanged right keeps the grant, the others get new grants; the grants of the employees left out, and
     * those whose right changed, are revoked. The report must have its owner saved first, if it has members.
     */
    public void saveOwnerMembers(ReportCustom report, Collection<OwnerMember> members) {
        if (!isAuthor()) {
            throw new AccessDeniedException("report owner members", String.valueOf(report.getId()), "save");
        }
        ReportCustom current = dataManager.load(ReportCustom.class)
                .id(report.getId())
                .fetchPlan(fp -> fp.add("businessOwner", FetchPlan.INSTANCE_NAME))
                .one();
        if (current.getBusinessOwner() == null && !members.isEmpty()) {
            throw new ReportAccessException(message("noBusinessOwner"));
        }

        Map<String, ReportAccessGrant> existing = new HashMap<>();
        ownerMembers(current).forEach(grant -> existing.put(grant.getEmployee().getId(), grant));
        Map<String, Boolean> wanted = new LinkedHashMap<>();
        members.forEach(member -> wanted.put(member.employee().getId(), member.canManage()));

        SaveContext saveContext = new SaveContext();
        String reason = message("ownerMembersChanged");
        existing.forEach((employeeId, grant) -> {
            Boolean canManage = wanted.get(employeeId);
            if (canManage == null || canManage != Boolean.TRUE.equals(grant.getCanManage())) {
                markRevoked(grant, reason);
                saveContext.saving(grant);
            }
        });
        Map<String, OrgStructureEmployee> employees = loadEmployees(wanted.keySet()).stream()
                .collect(Collectors.toMap(OrgStructureEmployee::getId, employee -> employee));
        Map<String, OrgStructurePosition> positions = currentPositions(employees.keySet());
        wanted.forEach((employeeId, canManage) -> {
            ReportAccessGrant grant = existing.get(employeeId);
            boolean unchanged = grant != null && canManage == Boolean.TRUE.equals(grant.getCanManage());
            OrgStructureEmployee employee = employees.get(employeeId);
            if (!unchanged && employee != null && !isDismissed(employee)) {
                saveContext.saving(newGrant(current, employee, positions.get(employeeId), ReportAccessGrantSource.OWNER,
                        canManage, null));
            }
        });
        if (!saveContext.getEntitiesToSave().isEmpty()) {
            dataManager.save(saveContext);
            changed();
        }
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private Set<UUID> personallyManaged(@Nullable Collection<UUID> reportIds) {
        if (reportIds != null && reportIds.isEmpty()) {
            return Set.of();
        }
        Optional<String> employeeId = currentEmployee().map(OrgStructureEmployee::getId);
        if (employeeId.isEmpty()) {
            return Set.of();
        }
        Set<UUID> managed = new HashSet<>(headedReports(employeeId.get(), reportIds, true));
        managed.addAll(grantedReports(employeeId.get(), reportIds, true));
        return managed;
    }

    /**
     * The reports whose owner the employee heads now, among the given ones or all; with
     * {@code activeOwnerOnly}, only those whose owner is not disbanded.
     */
    private Set<UUID> headedReports(String employeeId, @Nullable Collection<UUID> reportIds, boolean activeOwnerOnly) {
        String query = "select r.id from umida_ReportCustom r, umida_OrgStructureAssignments a" +
                " where a.employee.id = :" + EMPLOYEE_PARAM +
                " and a.position.subdivision = r.businessOwner" +
                " and a.position.isheadofsubdivision = 1 and " + active("a.position") +
                " and " + currentAssignment("a") +
                (activeOwnerOnly ? " and " + active("r.businessOwner") : "") +
                (reportIds != null ? " and r.id in :" + IDS_PARAM : "");
        var loader = dataManager.loadValues(query)
                .parameter(EMPLOYEE_PARAM, employeeId)
                .parameter(TODAY_PARAM, today());
        if (reportIds != null) {
            loader.parameter(IDS_PARAM, reportIds);
        }
        return reportIds(loader);
    }

    /**
     * The reports the employee has a grant in force for, among the given ones or all; with
     * {@code manageOnly}, a grant of a delegate for a report whose owner is not disbanded.
     */
    private Set<UUID> grantedReports(String employeeId, @Nullable Collection<UUID> reportIds, boolean manageOnly) {
        String query = "select g.report.id from umida_ReportAccessGrant g" +
                " where g.employee.id = :" + EMPLOYEE_PARAM + " and " + inForce("g") +
                (manageOnly ? " and g.canManage = true and " + active("g.report.businessOwner") : "") +
                (reportIds != null ? " and g.report.id in :" + IDS_PARAM : "");
        var loader = dataManager.loadValues(query)
                .parameter(EMPLOYEE_PARAM, employeeId)
                .parameter(TODAY_PARAM, today());
        if (reportIds != null) {
            loader.parameter(IDS_PARAM, reportIds);
        }
        return reportIds(loader);
    }

    /** The reports among the given ones with a primary or secondary domain the employee is responsible for. */
    private Set<UUID> domainReports(String employeeId, Collection<UUID> reportIds) {
        Set<Integer> domainIds = responsibleDomainIds(employeeId);
        if (domainIds.isEmpty()) {
            return Set.of();
        }
        return reportIds(dataManager.loadValues("select distinct r.id from umida_ReportCustom r" +
                        " left join r.domain d left join r.secondaryDomains sd" +
                        " where r.id in :" + IDS_PARAM +
                        " and (d.id in :domainIds or sd.id in :domainIds)")
                .parameter(IDS_PARAM, reportIds)
                .parameter("domainIds", domainIds));
    }

    /** The domains the employee is responsible for: as a steward in office, or as the head of their owner. */
    private Set<Integer> responsibleDomainIds(String employeeId) {
        LocalDate today = today();
        Set<Integer> domainIds = new HashSet<>();
        dataManager.loadValues("select s.domain.id from umida_DictDataDomainSteward s" +
                        " where s.employee.id = :" + EMPLOYEE_PARAM +
                        " and s.dateBegin <= :" + TODAY_PARAM +
                        " and (s.dateEnd is null or s.dateEnd >= :" + TODAY_PARAM + ")")
                .parameter(EMPLOYEE_PARAM, employeeId)
                .parameter(TODAY_PARAM, today)
                .properties("domainId")
                .list()
                .forEach(row -> domainIds.add(row.getValue("domainId")));
        dataManager.loadValues("select d.id from umida_DictDataDomain d, umida_OrgStructureAssignments a" +
                        " where a.employee.id = :" + EMPLOYEE_PARAM +
                        " and a.position.subdivision = d.businessOwner" +
                        " and a.position.isheadofsubdivision = 1 and " + active("a.position") +
                        " and " + currentAssignment("a"))
                .parameter(EMPLOYEE_PARAM, employeeId)
                .parameter(TODAY_PARAM, today)
                .properties("domainId")
                .list()
                .forEach(row -> domainIds.add(row.getValue("domainId")));
        return domainIds;
    }

    private List<ReportAccessGrant> grantsInForce(UUID reportId, Collection<String> employeeIds) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return dataManager.load(ReportAccessGrant.class)
                .query("select g from umida_ReportAccessGrant g" +
                        " where g.report.id = :reportId and g.employee.id in :employeeIds and " + inForce("g"))
                .parameter("reportId", reportId)
                .parameter("employeeIds", employeeIds)
                .parameter(TODAY_PARAM, today())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee", FetchPlan.INSTANCE_NAME))
                .list();
    }

    private List<OrgStructureEmployee> loadEmployees(Collection<String> employeeIds) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return dataManager.load(OrgStructureEmployee.class)
                .query("select e from umida_OrgStructureEmployee e where e.id in :ids")
                .parameter("ids", employeeIds)
                .list();
    }

    /**
     * The position each of the employees holds today, kept on a grant or a request: of several, a head
     * position first, then the latest assignment. An employee without a position in force is left out.
     */
    private Map<String, OrgStructurePosition> currentPositions(Collection<String> employeeIds) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        Comparator<OrgStructureAssignments> preferred = Comparator
                .comparing((OrgStructureAssignments a) -> Objects.equals(a.getPosition().getIsheadofsubdivision(), 1))
                .thenComparing(OrgStructureAssignments::getStartDate)
                .thenComparing(OrgStructureAssignments::getId, Comparator.reverseOrder());
        return dataManager.load(OrgStructureAssignments.class)
                .query("select a from umida_OrgStructureAssignments a" +
                        " where a.employee.id in :ids and a.position is not null" +
                        " and " + active("a.position") + " and " + currentAssignment("a"))
                .parameter("ids", employeeIds)
                .parameter(TODAY_PARAM, today())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("employee", FetchPlan.INSTANCE_NAME)
                        .add("position", FetchPlan.BASE))
                .list()
                .stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(),
                        Collectors.collectingAndThen(Collectors.maxBy(preferred),
                                a -> a.orElseThrow().getPosition())));
    }

    /** The request as it is now, which must still be pending and unchanged since the caller loaded it. */
    private ReportAccessRequest reloadPending(ReportAccessRequest request) {
        ReportAccessRequest current = dataManager.load(ReportAccessRequest.class)
                .id(request.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("report", FetchPlan.INSTANCE_NAME)
                        .add("employee", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElseThrow(() -> new ReportAccessException(message("requestAlreadyDecided")));
        boolean changed = request.getVersion() != null && !request.getVersion().equals(current.getVersion());
        if (changed || current.getStatus() != ReportAccessRequestStatus.PENDING) {
            throw new ReportAccessException(message("requestAlreadyDecided"));
        }
        return current;
    }

    private void requireManaged(ReportCustom report) {
        if (!canManageAccess(report)) {
            throw new AccessDeniedException("report access", String.valueOf(report.getId()), "manage");
        }
    }

    private void checkValidTo(@Nullable LocalDate validTo) {
        if (validTo != null && validTo.isBefore(today())) {
            throw new ReportAccessException(message("validToInPast"));
        }
    }

    private void decide(ReportAccessRequest request, ReportAccessRequestStatus status, @Nullable String comment) {
        request.setStatus(status);
        request.setDecidedBy(currentAuthentication.getUser().getUsername());
        request.setDecidedAt(now());
        request.setDecisionComment(comment);
    }

    private ReportAccessGrant newGrant(ReportCustom report, OrgStructureEmployee employee,
                                       @Nullable OrgStructurePosition position, ReportAccessGrantSource source,
                                       boolean canManage, @Nullable LocalDate validTo) {
        ReportAccessGrant grant = dataManager.create(ReportAccessGrant.class);
        grant.setReport(dataManager.getReference(ReportCustom.class, report.getId()));
        grant.setEmployee(employee);
        grant.setPosition(position);
        grant.setSource(source);
        grant.setCanManage(canManage);
        grant.setValidFrom(today());
        grant.setValidTo(validTo);
        return grant;
    }

    private void markRevoked(ReportAccessGrant grant, @Nullable String reason) {
        grant.setRevokedBy(currentAuthentication.getUser().getUsername());
        grant.setRevokedAt(now());
        grant.setRevokeReason(reason);
    }

    /** Dismissed from the day of the dismissal on, as for {@code EmployeeUserLinkService}. */
    private boolean isDismissed(OrgStructureEmployee employee) {
        return employee.getDismissalDate() != null && !employee.getDismissalDate().isAfter(today());
    }

    /**
     * The JPQL condition: the grant under {@code alias} is in force on the {@code today} parameter
     * ({@link uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql#TODAY_PARAM}) and its employee is not
     * dismissed.
     */
    public static String inForce(String alias) {
        return "(" + alias + ".revokedAt is null" +
                " and " + alias + ".validFrom <= :" + TODAY_PARAM +
                " and (" + alias + ".validTo is null or " + alias + ".validTo >= :" + TODAY_PARAM + ")" +
                " and " + notDismissed(alias + ".employee") + ")";
    }

    private static String notDismissed(String employeeAlias) {
        return "(" + employeeAlias + ".dismissalDate is null or " + employeeAlias + ".dismissalDate > :" + TODAY_PARAM + ")";
    }

    private static Set<UUID> reportIds(FluentValuesLoader loader) {
        return loader.properties("reportId")
                .list()
                .stream()
                .map(row -> row.<UUID>getValue("reportId"))
                .collect(Collectors.toSet());
    }

    /** Lets the screens counting the pending requests count them again. */
    private void changed() {
        eventPublisher.publishEvent(new ReportAccessChangedEvent(this));
    }

    private String message(String key) {
        return messages.getMessage(ReportAccessService.class, "reportAccess." + key);
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }

    private OffsetDateTime now() {
        return timeSource.now().toOffsetDateTime();
    }
}
