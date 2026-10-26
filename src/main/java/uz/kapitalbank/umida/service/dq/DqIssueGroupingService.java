package uz.kapitalbank.umida.service.dq;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.LoadContext;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.accesscontext.EntityAttributeContext;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.metamodel.model.MetaClass;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.dto.dq.DqIssueTreeItem;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.enums.dq.DqIssueGrouping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The grouped issue list: the issues under the value of the attribute they are grouped by, one level
 * deep, the groups by caption and the issues without a value last. Loads with the access rights of the
 * current user, so the tree holds the issues the paged list would.
 */
@Service
public class DqIssueGroupingService {

    private final DataManager dataManager;
    private final AccessManager accessManager;
    private final Metadata metadata;
    private final MetadataTools metadataTools;
    private final Messages messages;

    public DqIssueGroupingService(DataManager dataManager,
                                  AccessManager accessManager,
                                  Metadata metadata,
                                  MetadataTools metadataTools,
                                  Messages messages) {
        this.dataManager = dataManager;
        this.accessManager = accessManager;
        this.metadata = metadata;
        this.metadataTools = metadataTools;
        this.messages = messages;
    }

    /** The groupings offered to the current user: only by an attribute the user may see. */
    public List<DqIssueGrouping> availableGroupings() {
        MetaClass issueClass = metadata.getClass(DqIssue.class);
        return Arrays.stream(DqIssueGrouping.values())
                .filter(grouping -> grouping.getPropertyPath() == null || canView(issueClass, grouping.getPropertyPath()))
                .toList();
    }

    private boolean canView(MetaClass metaClass, String propertyPath) {
        EntityAttributeContext context = new EntityAttributeContext(metaClass.getPropertyPath(propertyPath));
        accessManager.applyRegisteredConstraints(context);
        return context.canView();
    }

    /**
     * Loads the issues the load context selects — the query, filters and order of the paged list — on
     * a single page, and groups them.
     */
    public List<DqIssueTreeItem> loadTree(LoadContext<DqIssue> loadContext, DqIssueGrouping grouping) {
        if (grouping == DqIssueGrouping.ALL) {
            return List.of();
        }
        LoadContext.Query query = loadContext.getQuery();
        if (query != null) {
            query.setFirstResult(0);
            query.setMaxResults(0);
        }
        return group(dataManager.loadList(loadContext), grouping);
    }

    /**
     * The group rows, each followed by its issue rows in the order given. A group caption is the
     * instance name of the value and the number of its issues.
     */
    public List<DqIssueTreeItem> group(List<DqIssue> issues, DqIssueGrouping grouping) {
        String propertyPath = Objects.requireNonNull(grouping.getPropertyPath(), "no attribute to group by");

        Map<Object, List<DqIssue>> issuesByKey = new LinkedHashMap<>();
        Map<Object, String> captionsByKey = new LinkedHashMap<>();
        for (DqIssue issue : issues) {
            Object value = EntityValues.getValueEx(issue, propertyPath);
            Object key = value == null ? null : EntityValues.getId(value);
            issuesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(issue);
            captionsByKey.computeIfAbsent(key, k -> value == null
                    ? messages.getMessage(DqIssueGrouping.class, "DqIssueGrouping." + grouping.name() + ".none")
                    : metadataTools.getInstanceName(value));
        }

        Comparator<Object> byCaption = Comparator
                .comparing((Object key) -> key == null)
                .thenComparing(key -> captionsByKey.get(key), String.CASE_INSENSITIVE_ORDER);

        List<DqIssueTreeItem> items = new ArrayList<>();
        issuesByKey.keySet().stream().sorted(byCaption).forEach(key -> {
            List<DqIssue> groupIssues = issuesByKey.get(key);

            DqIssueTreeItem group = metadata.create(DqIssueTreeItem.class);
            group.setId(groupId(grouping, key));
            group.setCaption("%s (%d)".formatted(captionsByKey.get(key), groupIssues.size()));
            group.setCount(groupIssues.size());
            items.add(group);

            for (DqIssue issue : groupIssues) {
                DqIssueTreeItem item = metadata.create(DqIssueTreeItem.class);
                item.setId(issue.getId());
                item.setParent(group);
                item.setCaption(issue.getCode());
                item.setIssue(issue);
                items.add(item);
            }
        });
        return items;
    }

    private static UUID groupId(DqIssueGrouping grouping, Object key) {
        return UUID.nameUUIDFromBytes((grouping.getId() + ":" + key).getBytes(StandardCharsets.UTF_8));
    }
}
