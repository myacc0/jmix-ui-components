package uz.kapitalbank.umida.service.report;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.LoadContext;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.accesscontext.EntityAttributeContext;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.reports.entity.Report;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportGrouping;

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
 * The grouped list of reports: the reports under the value of the attribute they are grouped by, one
 * level deep, the groups by caption and the reports without a value last. Loads with the access rights
 * of the current user, so the tree holds the reports the paged list would.
 */
@Service
public class ReportGroupingService {

    private final DataManager dataManager;
    private final AccessManager accessManager;
    private final Metadata metadata;
    private final MetadataTools metadataTools;
    private final Messages messages;

    public ReportGroupingService(DataManager dataManager,
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
    public List<ReportGrouping> availableGroupings() {
        MetaClass reportClass = metadata.getClass(ReportCustom.class);
        return Arrays.stream(ReportGrouping.values())
                .filter(grouping -> grouping.getPropertyPath() == null || canView(reportClass, grouping.getPropertyPath()))
                .toList();
    }

    private boolean canView(MetaClass metaClass, String propertyPath) {
        EntityAttributeContext context = new EntityAttributeContext(metaClass.getPropertyPath(propertyPath));
        accessManager.applyRegisteredConstraints(context);
        return context.canView();
    }

    /**
     * Loads the reports the load context selects — the query, filters and order of the paged list — on
     * a single page, and groups them.
     */
    public List<ReportTreeItem> loadTree(LoadContext<Report> loadContext, ReportGrouping grouping) {
        if (grouping == ReportGrouping.ALL) {
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
     * The group rows, each followed by its report rows in the order given. A group caption is the
     * instance name of the value and the number of its reports.
     */
    public List<ReportTreeItem> group(List<Report> reports, ReportGrouping grouping) {
        String propertyPath = Objects.requireNonNull(grouping.getPropertyPath(), "no attribute to group by");

        Map<Object, List<Report>> reportsByKey = new LinkedHashMap<>();
        Map<Object, String> captionsByKey = new LinkedHashMap<>();
        for (Report report : reports) {
            Object value = EntityValues.getValueEx(report, propertyPath);
            Object key = value == null ? null : EntityValues.getId(value);
            reportsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(report);
            captionsByKey.computeIfAbsent(key, k -> value == null
                    ? messages.getMessage(ReportGrouping.class, "ReportGrouping." + grouping.name() + ".none")
                    : metadataTools.getInstanceName(value));
        }

        Comparator<Object> byCaption = Comparator
                .comparing((Object key) -> key == null)
                .thenComparing(key -> captionsByKey.get(key), String.CASE_INSENSITIVE_ORDER);

        List<ReportTreeItem> items = new ArrayList<>();
        reportsByKey.keySet().stream().sorted(byCaption).forEach(key -> {
            List<Report> groupReports = reportsByKey.get(key);

            ReportTreeItem group = metadata.create(ReportTreeItem.class);
            group.setId(groupId(grouping, key));
            group.setCaption("%s (%d)".formatted(captionsByKey.get(key), groupReports.size()));
            group.setCount(groupReports.size());
            items.add(group);

            for (Report report : groupReports) {
                ReportTreeItem item = metadata.create(ReportTreeItem.class);
                item.setId(report.getId());
                item.setParent(group);
                item.setCaption(metadataTools.getInstanceName(report));
                item.setReport((ReportCustom) report);
                items.add(item);
            }
        });
        return items;
    }

    private static UUID groupId(ReportGrouping grouping, Object key) {
        return UUID.nameUUIDFromBytes((grouping.getId() + ":" + key).getBytes(StandardCharsets.UTF_8));
    }
}
