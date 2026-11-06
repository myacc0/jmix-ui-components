package uz.kapitalbank.umida.service.report;

import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.security.AccessDeniedException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureAssignments;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportAccessGrant;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMember;
import uz.kapitalbank.umida.service.report.ReportAccessService.OwnerMemberChange;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.TODAY_PARAM;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.active;
import static uz.kapitalbank.umida.service.orgstructure.OrgStructureJpql.currentAssignment;

/**
 * The list of the business owner of a report as its editor shows it: the employees of the owner subdivision
 * and of every active subdivision below it, one row each, with the rights of the list ticked. Saved whole
 * when the owner is picked, through {@link ReportAccessService#saveOwnerMembers}, which keeps it to the
 * authors, and otherwise row by row, the rows changed only, through {@link #saveChanges}.
 */
@Service
public class ReportOwnerMemberService {

    private final UnconstrainedDataManager dataManager;
    private final ReportAccessService accessService;
    private final Metadata metadata;
    private final TimeSource timeSource;

    public ReportOwnerMemberService(UnconstrainedDataManager dataManager,
                                    ReportAccessService accessService,
                                    Metadata metadata,
                                    TimeSource timeSource) {
        this.dataManager = dataManager;
        this.accessService = accessService;
        this.metadata = metadata;
        this.timeSource = timeSource;
    }

    /**
     * The rows for the report with the given owner, the head first, then by subdivision and name.
     *
     * @param owner    the owner as the editor has it now, saved or not; none gives no rows
     * @param defaults the owner was just picked: every employee runs the report, no one manages the access.
     *                 Otherwise the rows show the list as saved, and a member who has left the subdivisions
     *                 of the owner gets a row of their own
     */
    public List<ReportOwnerMemberItem> items(ReportCustom report, @Nullable OrgStructureSubdivision owner,
                                             boolean defaults) {
        if (owner == null) {
            return List.of();
        }
        Map<String, ReportAccessGrant> members = new HashMap<>();
        if (!defaults && report.getId() != null) {
            accessService.ownerMembers(report).forEach(grant -> members.put(grant.getEmployee().getId(), grant));
        }

        Map<String, ReportOwnerMemberItem> items = subtreeItems(owner);
        for (ReportOwnerMemberItem item : items.values()) {
            ReportAccessGrant grant = members.get(item.getEmployee().getId());
            item.setManage(item.getHead() || !defaults && grant != null && Boolean.TRUE.equals(grant.getCanManage()));
            item.setRun(item.getHead() || defaults || grant != null);
        }
        members.forEach((employeeId, grant) -> {
            if (!items.containsKey(employeeId)) {
                ReportOwnerMemberItem item = newItem(grant.getEmployee());
                item.setOutside(true);
                item.setRun(true);
                item.setManage(Boolean.TRUE.equals(grant.getCanManage()));
                items.put(employeeId, item);
            }
        });

        return sorted(items.values());
    }

    /**
     * The employees of the subdivision and of the active subdivisions below it, one row each, the head of the
     * subdivision first, nothing ticked: those the owner picks from to give access to a whole subdivision.
     */
    public List<ReportOwnerMemberItem> employeesOf(OrgStructureSubdivision subdivision) {
        return sorted(subtreeItems(subdivision).values());
    }

    /** The ids of the employees currently holding a position in the subdivision or below it. */
    public Set<String> subtreeEmployeeIds(OrgStructureSubdivision subdivision) {
        return subtreeItems(subdivision).keySet();
    }

    /** One row per employee of the subtree, keyed by the employee id, the head of {@code root} marked. */
    private Map<String, ReportOwnerMemberItem> subtreeItems(OrgStructureSubdivision root) {
        Map<String, ReportOwnerMemberItem> items = new LinkedHashMap<>();
        for (OrgStructureAssignments assignment : currentAssignments(subtreeIds(root))) {
            OrgStructurePosition position = assignment.getPosition();
            boolean head = Objects.equals(position.getIsheadofsubdivision(), 1)
                    && root.getId().equals(position.getSubdivision().getId());
            ReportOwnerMemberItem item = items.get(assignment.getEmployee().getId());
            if (item == null) {
                item = newItem(assignment.getEmployee());
                items.put(assignment.getEmployee().getId(), item);
                describe(item, position);
            }
            if (head && !item.getHead()) {
                // the head position names the row, whatever other position the employee holds below
                item.setHead(true);
                describe(item, position);
            }
        }
        return items;
    }

    private static List<ReportOwnerMemberItem> sorted(Collection<ReportOwnerMemberItem> items) {
        List<ReportOwnerMemberItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing((ReportOwnerMemberItem item) -> item.getHead() ? 0 : 1)
                .thenComparing(item -> item.getOutside() ? 1 : 0)
                .thenComparing(ReportOwnerMemberItem::getSubdivisionName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReportOwnerMemberItem::getEmployeeName, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    /**
     * Saves the ticked rows as the list of the business owner of the saved report, whole: the list of an owner
     * just picked. The head is no member: the head has the rights anyway. Rows with neither right leave the list.
     */
    public void save(ReportCustom report, Collection<ReportOwnerMemberItem> items) {
        List<OwnerMember> members = items.stream()
                .filter(item -> !item.getHead())
                .filter(item -> item.getRun() || item.getManage())
                .map(item -> new OwnerMember(item.getEmployee(), item.getManage()))
                .toList();
        accessService.saveOwnerMembers(report, members);
    }

    /**
     * Saves the rows the user changed on the list of the business owner of the saved report, and those
     * alone, see {@link ReportAccessService#changeOwnerMembers}: the rows changed by someone else meanwhile
     * stay as they are. The head has the rights anyway, so the row of the head changes nothing. A new member
     * has to be an employee of the owner or of a subdivision below it; one on the list already keeps the
     * row after leaving them.
     */
    public void saveChanges(ReportCustom report, Collection<ReportOwnerMemberItem> changed) {
        List<ReportOwnerMemberItem> rows = changed.stream().filter(item -> !item.getHead()).toList();
        if (rows.isEmpty()) {
            return;
        }
        ReportCustom current = dataManager.load(ReportCustom.class)
                .id(report.getId())
                .fetchPlan(fp -> fp.add("businessOwner", FetchPlan.INSTANCE_NAME))
                .one();
        Set<String> allowed = new HashSet<>();
        if (current.getBusinessOwner() != null) {
            allowed.addAll(subtreeEmployeeIds(current.getBusinessOwner()));
        }
        accessService.ownerMembers(current).forEach(grant -> allowed.add(grant.getEmployee().getId()));
        boolean foreign = rows.stream()
                .anyMatch(item -> (item.getRun() || item.getManage()) && !allowed.contains(item.getEmployee().getId()));
        if (foreign) {
            throw new AccessDeniedException("report owner members", String.valueOf(report.getId()), "add foreign");
        }
        accessService.changeOwnerMembers(current, rows.stream()
                .map(item -> new OwnerMemberChange(item.getEmployee(), item.getRun() || item.getManage(), item.getManage()))
                .toList());
    }

    /** The owner and every active subdivision below it, whatever the depth. */
    private Set<String> subtreeIds(OrgStructureSubdivision owner) {
        Set<String> ids = new LinkedHashSet<>(List.of(owner.getId()));
        Set<String> level = Set.of(owner.getId());
        while (!level.isEmpty()) {
            Set<String> children = new HashSet<>();
            dataManager.loadValues("select s.id from umida_OrgStructureSubdivision s" +
                            " where s.parent.id in :parentIds and " + active("s"))
                    .parameter("parentIds", level)
                    .properties("id")
                    .list()
                    .forEach(row -> {
                        String id = row.getValue("id");
                        if (ids.add(id)) {
                            children.add(id);
                        }
                    });
            level = children;
        }
        return ids;
    }

    /** The current assignments of the employees not dismissed to the active positions of the subdivisions. */
    private List<OrgStructureAssignments> currentAssignments(Collection<String> subdivisionIds) {
        return dataManager.load(OrgStructureAssignments.class)
                .query("select a from umida_OrgStructureAssignments a" +
                        " where a.position.subdivision.id in :subdivisionIds and a.employee is not null" +
                        " and " + active("a.position") + " and " + currentAssignment("a") +
                        " and (a.employee.dismissalDate is null or a.employee.dismissalDate > :" + TODAY_PARAM + ")")
                .parameter("subdivisionIds", subdivisionIds)
                .parameter(TODAY_PARAM, today())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("employee", FetchPlan.BASE)
                        .add("position", position -> position.addFetchPlan(FetchPlan.BASE)
                                .add("subdivision", FetchPlan.BASE)
                                .add("jobTitle", FetchPlan.BASE)))
                .list();
    }

    private ReportOwnerMemberItem newItem(OrgStructureEmployee employee) {
        ReportOwnerMemberItem item = metadata.create(ReportOwnerMemberItem.class);
        item.setId(UUID.nameUUIDFromBytes(employee.getId().getBytes(StandardCharsets.UTF_8)));
        item.setEmployee(employee);
        item.setEmployeeName(employee.getFullName());
        return item;
    }

    private static void describe(ReportOwnerMemberItem item, OrgStructurePosition position) {
        item.setPositionName(position.getJobTitle() != null ? position.getJobTitle().getName() : null);
        item.setSubdivisionName(position.getSubdivision() != null ? position.getSubdivision().getName() : null);
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }
}
