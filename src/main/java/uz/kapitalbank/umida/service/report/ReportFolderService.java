package uz.kapitalbank.umida.service.report;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.MetadataTools;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.enums.report.ReportTileSort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reports seen as a file system: the report groups are the folders, nested by their parent, and the
 * reports are the files of the folder of their group. The top level holds the groups without a parent and
 * the reports without a group. Loads with the access rights of the current user: a group the user may not
 * see is no folder, and its subgroups go up to the top level.
 */
@Service
public class ReportFolderService {

    private final DataManager dataManager;
    private final MetadataTools metadataTools;
    private final CurrentAuthentication currentAuthentication;

    public ReportFolderService(DataManager dataManager,
                               MetadataTools metadataTools,
                               CurrentAuthentication currentAuthentication) {
        this.dataManager = dataManager;
        this.metadataTools = metadataTools;
        this.currentAuthentication = currentAuthentication;
    }

    /**
     * What a folder holds, the subgroups first and then the reports, each in the order asked for.
     *
     * @param group the group of the folder; {@code null} for the top level
     * @param path  the groups from the top level down to the group of the folder, both included; empty at the top level
     */
    public record Folder(@Nullable ReportGroupCustom group,
                         List<ReportGroupCustom> path,
                         List<ReportGroupCustom> groups,
                         List<ReportCustom> reports) {

        public boolean isTop() {
            return group == null;
        }

        public boolean isEmpty() {
            return groups.isEmpty() && reports.isEmpty();
        }
    }

    /**
     * The folder of the group, or the top level if the group is {@code null} or no longer there.
     *
     * @param mine whether only the reports created by the current user are shown; the groups without
     *             such reports anywhere below them are then left out as well
     * @param sort the order of the subgroups and, apart from them, of the reports
     */
    public Folder loadFolder(@Nullable UUID groupId, boolean mine, ReportTileSort sort) {
        Map<UUID, ReportGroupCustom> groupsById = dataManager.load(ReportGroupCustom.class)
                .all()
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("parent", FetchPlan.INSTANCE_NAME))
                .list()
                .stream()
                .collect(Collectors.toMap(ReportGroupCustom::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        ReportGroupCustom group = groupId != null ? groupsById.get(groupId) : null;
        Set<UUID> shownGroupIds = mine ? groupsWithReportsOf(currentUsername(), groupsById) : groupsById.keySet();

        List<ReportGroupCustom> groups = groupsById.values().stream()
                .filter(child -> shownGroupIds.contains(child.getId()))
                .filter(child -> Objects.equals(parentId(child, groupsById), group != null ? group.getId() : null))
                .sorted(comparator(sort, ReportGroupCustom::getCreateTs))
                .toList();

        return new Folder(group, path(group, groupsById), groups, loadReports(group, mine, sort));
    }

    /**
     * The order of groups or of reports: by the localized name, case aside, or by the time of creation with
     * the name between the ones created at the same time.
     */
    private <E> Comparator<E> comparator(ReportTileSort sort, Function<E, Date> createTs) {
        Comparator<E> byName = Comparator.comparing(metadataTools::getInstanceName, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case NAME_ASC -> byName;
            case NAME_DESC -> byName.reversed();
            case NEWEST_FIRST -> Comparator.comparing(createTs, Comparator.nullsLast(Comparator.<Date>reverseOrder()))
                    .thenComparing(byName);
            case OLDEST_FIRST -> Comparator.comparing(createTs, Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    .thenComparing(byName);
        };
    }

    private List<ReportCustom> loadReports(@Nullable ReportGroupCustom group, boolean mine, ReportTileSort sort) {
        String query = "select e from umida_ReportCustom e where "
                + (group != null ? "e.group.id = :groupId" : "e.group is null")
                + (mine ? " and e.createdBy = :createdBy" : "");
        var loader = dataManager.load(ReportCustom.class)
                .query(query)
                // the time of creation is for the order, report.view of the add-on leaves it out
                .fetchPlan(fp -> fp.addFetchPlan("report.view").add("createTs"));
        if (group != null) {
            loader.parameter("groupId", group.getId());
        }
        if (mine) {
            loader.parameter("createdBy", currentUsername());
        }
        return loader.list().stream()
                .sorted(comparator(sort, ReportCustom::getCreateTs))
                .toList();
    }

    /** The groups holding a report of the user, directly or in a group below them. */
    private Set<UUID> groupsWithReportsOf(String username, Map<UUID, ReportGroupCustom> groupsById) {
        Set<UUID> result = new HashSet<>();
        dataManager.loadValues("select distinct g.id from umida_ReportCustom e join e.group g where e.createdBy = :createdBy")
                .parameter("createdBy", username)
                .properties("groupId")
                .list()
                .forEach(row -> {
                    UUID id = row.getValue("groupId");
                    // the parents are followed until a group already in: its parents are in as well
                    while (id != null && groupsById.containsKey(id) && result.add(id)) {
                        id = parentId(groupsById.get(id), groupsById);
                    }
                });
        return result;
    }

    /** The groups from the top level down to the group; a cycle in the data stops the walk. */
    private static List<ReportGroupCustom> path(@Nullable ReportGroupCustom group, Map<UUID, ReportGroupCustom> groupsById) {
        List<ReportGroupCustom> path = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ReportGroupCustom current = group; current != null && seen.add(current.getId());
             current = groupsById.get(parentId(current, groupsById))) {
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    /** The parent of the group if the user may see it; {@code null} puts the group on the top level. */
    @Nullable
    private static UUID parentId(ReportGroupCustom group, Map<UUID, ReportGroupCustom> groupsById) {
        ReportGroupCustom parent = group.getParent();
        return parent != null && groupsById.containsKey(parent.getId()) ? parent.getId() : null;
    }

    private String currentUsername() {
        return currentAuthentication.getUser().getUsername();
    }
}
