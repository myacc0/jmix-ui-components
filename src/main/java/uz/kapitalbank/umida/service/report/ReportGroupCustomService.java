package uz.kapitalbank.umida.service.report;

import io.jmix.core.DataManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The look of the report groups and the rules of their hierarchy. */
@Service
public class ReportGroupCustomService {

    public static final String ICONSET = "umida-icons";
    public static final String DEFAULT_ICON = "folder";
    public static final String DEFAULT_COLOR = "#CDD0D5";

    /** The palette a group color is picked from, the default one first. */
    public static final List<String> COLORS = List.of(
            DEFAULT_COLOR, "#F6C86B", "#FD9575", "#FF7E75", "#EF8FB1",
            "#F395E6", "#BAA2EC", "#7B96F0", "#8FC7F7", "#4FC5DF", "#76CDB9", "#9EDB8F");

    /**
     * The icons a group icon is picked from, in the order the editor shows them: names of the "umida-icons"
     * iconset, as {@code <vaadin-icon icon="umida-icons:<name>">} takes them, that is the file names of
     * {@code META-INF/resources/icons} without {@code .svg}.
     */
    public static final List<String> ICONS = List.of(
            DEFAULT_ICON, "folder-open", "folder-match", "computer", "attach-email", "reports",
            "bar-chart", "bar-chart-4-bars", "pie-chart", "cog", "contact-mail", "group-search",
            "finance-mode", "data-quality", "person-text", "inventory", "business-center", "calendar",
            "clock", "work-history", "groups", "user", "person-check", "frame-person",
            "article-person", "structure", "school", "home", "photo", "security",
            "key", "mail", "phone", "star", "book-ribbon", "code-xml");

    private final DataManager dataManager;

    public ReportGroupCustomService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<String> getIcons() {
        return ICONS;
    }

    public String getIconOrDefault(@Nullable String icon) {
        return icon == null || icon.isBlank() ? DEFAULT_ICON : icon;
    }

    public String getColorOrDefault(@Nullable String color) {
        return color == null || color.isBlank() ? DEFAULT_COLOR : color;
    }

    /**
     * The ids of the group and of all the groups below it: none of them may become the parent of the
     * group, or the hierarchy would get a cycle. A new group has no groups below it yet.
     */
    public Set<UUID> getSelfAndDescendantIds(ReportGroupCustom group) {
        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        dataManager.loadValues("select g.id, p.id from umida_ReportGroupCustom g join g.parent p")
                .properties("id", "parentId")
                .list()
                .forEach(row -> childrenByParent
                        .computeIfAbsent(row.<UUID>getValue("parentId"), id -> new ArrayList<>())
                        .add(row.<UUID>getValue("id")));

        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(group.getId());
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (result.add(id)) {
                queue.addAll(childrenByParent.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    /** Whether {@code parent} lies below {@code group} or is the group itself. */
    public boolean isCyclicParent(ReportGroupCustom group, @Nullable ReportGroupCustom parent) {
        return parent != null && getSelfAndDescendantIds(group).contains(parent.getId());
    }
}
