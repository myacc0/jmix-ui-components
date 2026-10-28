package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.MetadataTools;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.util.RemoveOperation;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.component.reportgroup.ReportGroupBadge;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.Collection;
import java.util.List;

/**
 * The report groups of the database, next to the add-on's {@code report_ReportGroup.list}, which stays the default
 * list of report groups: the grid actions open {@link ReportGroupCustomDetailView} by id.
 */
@Route(value = "report-groups", layout = MainView.class)
@ViewController(id = "umida_ReportGroupCustom.list")
@ViewDescriptor(path = "report-group-custom-list-view.xml")
@LookupComponent("reportGroupsDataGrid")
@DialogMode(width = "64em")
public class ReportGroupCustomListView extends StandardListView<ReportGroupCustom> {

    @Autowired
    private ReportGroupCustomService reportGroupCustomService;
    @Autowired
    private MetadataTools metadataTools;

    @ViewComponent
    private CollectionLoader<ReportGroupCustom> reportGroupsDl;

    /**
     * The groups the lookup must not offer, see {@link #setExcludedGroupIds}. It carries an empty list
     * by default, and the loader then skips it.
     */
    private final PropertyCondition excludedCondition = PropertyCondition.notInList("id", List.of()).skipNullOrEmpty();

    /** Adds the exclusion next to the conditions of the column header filters, which share the root condition. */
    @Subscribe
    public void onInit(final InitEvent event) {
        Condition root = reportGroupsDl.getCondition();
        LogicalCondition and = root instanceof LogicalCondition logical ? logical : LogicalCondition.and();
        if (root != null && root != and) {
            and.add(root);
        }
        and.add(excludedCondition);
        reportGroupsDl.setCondition(and);
    }

    /** Hides the groups from the list: the parent lookup of a group excludes the group itself and the groups below it. */
    public void setExcludedGroupIds(Collection<?> ids) {
        excludedCondition.setParameterValue(ids);
    }

    @Supply(to = "reportGroupsDataGrid.badge", subject = "renderer")
    private Renderer<ReportGroupCustom> reportGroupsDataGridBadgeRenderer() {
        return new ComponentRenderer<>(group -> new ReportGroupBadge(
                reportGroupCustomService.getIconOrDefault(group.getIcon()),
                reportGroupCustomService.getColorOrDefault(group.getColor())));
    }

    /** The localized title, as the add-on shows it. */
    @Supply(to = "reportGroupsDataGrid.title", subject = "renderer")
    private Renderer<ReportGroupCustom> reportGroupsDataGridTitleRenderer() {
        return new TextRenderer<>(metadataTools::getInstanceName);
    }

    /** The subgroups of a removed group are unlinked from it in the database: reloaded, the list shows them without a parent. */
    @Install(to = "reportGroupsDataGrid.removeAction", subject = "afterActionPerformedHandler")
    private void reportGroupsDataGridRemoveActionAfterActionPerformedHandler(
            final RemoveOperation.AfterActionPerformedEvent<ReportGroupCustom> event) {
        reportGroupsDl.load();
    }
}
