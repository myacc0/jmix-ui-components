package uz.kapitalbank.umida.view.dq;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.LoadContext;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.facet.UrlQueryParametersFacet;
import io.jmix.flowui.facet.urlqueryparameters.AbstractUrlQueryParametersBinder;
import io.jmix.flowui.kit.component.HasActions;
import io.jmix.flowui.kit.component.button.JmixButton;
import uz.kapitalbank.umida.dto.dq.DqIssueTreeItem;
import uz.kapitalbank.umida.enums.dq.DqIssueGrouping;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqIssueGroupingService;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * The issues, either as one paged list or — when a grouping is chosen — as a tree of all of them under
 * their rule group, domain or assignee subdivision, without pages. The tree holds what the paged list
 * selects, its column filters and order included. The buttons act on the issue selected in the grid
 * shown; a group row selects no issue.
 * <p>
 * The grouping is kept in the URL, so that coming back from an issue opened from the tree returns to it.
 */
@Route(value = "dq-issues", layout = MainView.class)
@ViewController(id = "umida_DqIssue.list")
@ViewDescriptor(path = "dq-issue-list-view.xml")
@LookupComponent("dqIssuesDataGrid")
@DialogMode(width = "64em")
public class DqIssueListView extends StandardListView<DqIssue> {

    /** The column showing the value the issues are grouped by, hidden in the tree: the group row shows it. */
    private static final Map<DqIssueGrouping, String> GROUPING_COLUMNS = Map.of(
            DqIssueGrouping.GROUP, "ruleGroup",
            DqIssueGrouping.DOMAIN, "ruleDomain",
            DqIssueGrouping.ASSIGNEE, "assignee");

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private ViewNavigators viewNavigators;

    @ViewComponent
    private DataGrid<DqIssue> dqIssuesDataGrid;

    @ViewComponent
    private CollectionLoader<DqIssue> dqIssuesDl;

    @ViewComponent
    private CollectionContainer<DqIssue> dqIssuesDc;

    @ViewComponent
    private TreeDataGrid<DqIssueTreeItem> dqIssuesTreeDataGrid;

    @ViewComponent
    private CollectionLoader<DqIssueTreeItem> dqIssueTreeItemsDl;

    @ViewComponent
    private CollectionContainer<DqIssueTreeItem> dqIssueTreeItemsDc;

    @ViewComponent
    private JmixSelect<DqIssueGrouping> groupingField;

    @ViewComponent
    private SimplePagination pagination;

    @ViewComponent
    private JmixButton readButton;

    @ViewComponent
    private JmixButton violationsButton;

    @ViewComponent
    private JmixButton closeIssueButton;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqIssueService dqIssueService;

    @Autowired
    private DqIssueGroupingService dqIssueGroupingService;

    private DqIssueGrouping grouping = DqIssueGrouping.ALL;

    /** The groups expanded before the tree is reloaded. */
    private Set<UUID> expandedGroupIds = Set.of();

    @Supply(to = "dqIssuesDataGrid.status", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_STATUS, DqIssue::getStatus);
    }

    @Supply(to = "dqIssuesDataGrid.closingReason", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridClosingReasonRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_CLOSING_REASON, DqIssue::getClosingReason);
    }

    @Supply(to = "dqIssuesDataGrid.severity", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, dqIssue -> dqIssue.getRule().getSeverity());
    }

    @Supply(to = "dqIssuesTreeDataGrid.status", subject = "renderer")
    private Renderer<DqIssueTreeItem> dqIssuesTreeDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_STATUS, item -> item.isGroup() ? null : item.getIssue().getStatus());
    }

    @Supply(to = "dqIssuesTreeDataGrid.closingReason", subject = "renderer")
    private Renderer<DqIssueTreeItem> dqIssuesTreeDataGridClosingReasonRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_CLOSING_REASON,
                item -> item.isGroup() ? null : item.getIssue().getClosingReason());
    }

    @Supply(to = "dqIssuesTreeDataGrid.severity", subject = "renderer")
    private Renderer<DqIssueTreeItem> dqIssuesTreeDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY,
                item -> item.isGroup() ? null : item.getIssue().getRule().getSeverity());
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        groupingField.setItems(dqIssueGroupingService.availableGroupings());
        groupingField.setValue(DqIssueGrouping.ALL);
        ViewControllerUtils.getViewFacet(this, UrlQueryParametersFacet.class)
                .registerBinder(new GroupingBinder());
        // the widths fit the group rows the tree opens with; the issue rows of an expanded group need more
        dqIssuesTreeDataGrid.addExpandListener(expandEvent -> dqIssuesTreeDataGrid.recalculateColumnWidths());
        updateCloseIssueActionVisibility();
    }

    @Subscribe("groupingField")
    public void onGroupingFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<JmixSelect<DqIssueGrouping>, DqIssueGrouping> event) {
        if (event.getValue() != null) {
            setGrouping(event.getValue());
        }
    }

    /** Shows the paged list or the tree, binds the buttons to the actions of the grid shown and reloads it. */
    private void setGrouping(DqIssueGrouping grouping) {
        if (this.grouping == grouping) {
            return;
        }
        this.grouping = grouping;

        boolean grouped = isGrouped();
        dqIssuesDataGrid.setVisible(!grouped);
        pagination.setVisible(!grouped);
        dqIssuesTreeDataGrid.setVisible(grouped);
        bindButtons(grouped ? dqIssuesTreeDataGrid : dqIssuesDataGrid);

        if (grouped) {
            GROUPING_COLUMNS.forEach((columnGrouping, key) -> {
                // a column the user may not see is not in the grid
                DataGridColumn<DqIssueTreeItem> column = dqIssuesTreeDataGrid.getColumnByKey(key);
                if (column != null) {
                    column.setVisible(columnGrouping != grouping);
                }
            });
            dqIssueTreeItemsDl.load();
        } else {
            dqIssuesDl.load();
        }
        updateCloseIssueActionVisibility();
    }

    private boolean isGrouped() {
        return grouping != DqIssueGrouping.ALL;
    }

    /** The buttons keep their own look: icon only, with the title as the tooltip. */
    private void bindButtons(HasActions grid) {
        bindButton(readButton, grid, "readAction");
        bindButton(violationsButton, grid, "violationsAction");
        bindButton(closeIssueButton, grid, "closeIssueAction");
    }

    private static void bindButton(JmixButton button, HasActions grid, String actionId) {
        button.setAction(grid.getAction(actionId), false);
        button.setText("");
    }

    @Install(to = "dqIssueTreeItemsDl", target = Target.DATA_LOADER)
    private List<DqIssueTreeItem> dqIssueTreeItemsDlLoadDelegate(final LoadContext<DqIssueTreeItem> loadContext) {
        return dqIssueGroupingService.loadTree(dqIssuesDl.createLoadContext(), grouping);
    }

    /** A reload brings new rows: the groups that were expanded are expanded again. */
    @Subscribe(id = "dqIssueTreeItemsDl", target = Target.DATA_LOADER)
    public void onDqIssueTreeItemsDlPreLoad(final CollectionLoader.PreLoadEvent<DqIssueTreeItem> event) {
        expandedGroupIds = dqIssueTreeItemsDc.getItems().stream()
                .filter(item -> item.isGroup() && dqIssuesTreeDataGrid.isExpanded(item))
                .map(DqIssueTreeItem::getId)
                .collect(Collectors.toSet());
    }

    @Subscribe(id = "dqIssueTreeItemsDl", target = Target.DATA_LOADER)
    public void onDqIssueTreeItemsDlPostLoad(final CollectionLoader.PostLoadEvent<DqIssueTreeItem> event) {
        dqIssuesTreeDataGrid.expand(event.getLoadedEntities().stream()
                .filter(item -> expandedGroupIds.contains(item.getId()))
                .toList());
    }

    /** A group opens and closes on a double click, an issue opens. */
    @Subscribe("dqIssuesTreeDataGrid")
    public void onDqIssuesTreeDataGridItemDoubleClick(final ItemDoubleClickEvent<DqIssueTreeItem> event) {
        DqIssueTreeItem item = event.getItem();
        if (item == null) {
            return;
        }
        if (!item.isGroup()) {
            readIssue(item.getIssue());
        } else if (dqIssuesTreeDataGrid.isExpanded(item)) {
            dqIssuesTreeDataGrid.collapse(item);
        } else {
            dqIssuesTreeDataGrid.expand(item);
        }
    }

    /** Only an open issue can be closed, and only by a user of the subdivision its rule is assigned to. */
    @Install(to = "dqIssuesDataGrid.closeIssueAction", subject = "enabledRule")
    private boolean closeIssueActionEnabledRule() {
        return dqIssueService.canClose(selectedListIssue());
    }

    @Install(to = "dqIssuesTreeDataGrid.closeIssueAction", subject = "enabledRule")
    private boolean treeCloseIssueActionEnabledRule() {
        return dqIssueService.canClose(selectedTreeIssue());
    }

    @Subscribe(id = "dqIssuesDc", target = Target.DATA_CONTAINER)
    public void onDqIssuesDcItemChange(final InstanceContainer.ItemChangeEvent<DqIssue> event) {
        updateCloseIssueActionVisibility();
    }

    /** A reload keeps the selection but brings a fresh instance of the selected issue, maybe closed meanwhile. */
    @Subscribe(id = "dqIssuesDc", target = Target.DATA_CONTAINER)
    public void onDqIssuesDcCollectionChange(final CollectionContainer.CollectionChangeEvent<DqIssue> event) {
        updateCloseIssueActionVisibility();
    }

    @Subscribe(id = "dqIssueTreeItemsDc", target = Target.DATA_CONTAINER)
    public void onDqIssueTreeItemsDcItemChange(final InstanceContainer.ItemChangeEvent<DqIssueTreeItem> event) {
        updateCloseIssueActionVisibility();
    }

    @Subscribe(id = "dqIssueTreeItemsDc", target = Target.DATA_CONTAINER)
    public void onDqIssueTreeItemsDcCollectionChange(final CollectionContainer.CollectionChangeEvent<DqIssueTreeItem> event) {
        updateCloseIssueActionVisibility();
    }

    /** The close button is not shown at all for an issue the current user cannot close. */
    private void updateCloseIssueActionVisibility() {
        dqIssuesDataGrid.getAction("closeIssueAction").setVisible(dqIssueService.canClose(selectedListIssue()));
        dqIssuesTreeDataGrid.getAction("closeIssueAction").setVisible(dqIssueService.canClose(selectedTreeIssue()));
    }

    /** The loaded instance of the issue selected in the grid shown. */
    @Nullable
    private DqIssue selectedIssue() {
        return isGrouped() ? selectedTreeIssue() : selectedListIssue();
    }

    /** The loaded instance of the selected issue: after a reload the grid may still hold the previous one. */
    @Nullable
    private DqIssue selectedListIssue() {
        DqIssue selected = dqIssuesDataGrid.getSingleSelectedItem();
        return selected != null ? dqIssuesDc.getItemOrNull(selected.getId()) : null;
    }

    /** The loaded issue of the selected tree row; none for a group row. */
    @Nullable
    private DqIssue selectedTreeIssue() {
        DqIssueTreeItem selected = dqIssuesTreeDataGrid.getSingleSelectedItem();
        DqIssueTreeItem loaded = selected != null ? dqIssueTreeItemsDc.getItemOrNull(selected.getId()) : null;
        return loaded != null ? loaded.getIssue() : null;
    }

    @Install(to = "dqIssuesTreeDataGrid.readAction", subject = "enabledRule")
    private boolean treeReadActionEnabledRule() {
        return selectedTreeIssue() != null;
    }

    /** What the read action of the paged list does: the issue page, read-only, coming back here. */
    @Subscribe("dqIssuesTreeDataGrid.readAction")
    public void onDqIssuesTreeDataGridRead(final ActionPerformedEvent event) {
        readIssue(selectedTreeIssue());
    }

    private void readIssue(@Nullable DqIssue issue) {
        if (issue == null) {
            return;
        }
        viewNavigators.detailView(this, DqIssue.class)
                .editEntity(issue)
                .withBackwardNavigation(true)
                .withReadOnly(true)
                .navigate();
    }

    @Subscribe("dqIssuesDataGrid.closeIssueAction")
    public void onDqIssuesDataGridCloseIssue(final ActionPerformedEvent event) {
        closeIssue();
    }

    @Subscribe("dqIssuesTreeDataGrid.closeIssueAction")
    public void onDqIssuesTreeDataGridCloseIssue(final ActionPerformedEvent event) {
        closeIssue();
    }

    private void closeIssue() {
        DqIssue issue = selectedIssue();
        if (!dqIssueService.canClose(issue)) {
            return;
        }

        dialogWindows.detail(this, DqIssue.class)
                .editEntity(issue)
                .withViewClass(DqIssueCloseView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        (isGrouped() ? dqIssueTreeItemsDl : dqIssuesDl).load();
                    }
                })
                .open();
    }

    /**
     * The rows the latest failed check found — or, for a cross-source rule, which finds no rows,
     * the values the check compared.
     */
    @Install(to = "dqIssuesDataGrid.violationsAction", subject = "enabledRule")
    private boolean violationsActionEnabledRule() {
        return hasViolations(dqIssuesDataGrid.getSingleSelectedItem());
    }

    @Install(to = "dqIssuesTreeDataGrid.violationsAction", subject = "enabledRule")
    private boolean treeViolationsActionEnabledRule() {
        return hasViolations(selectedTreeIssue());
    }

    private static boolean hasViolations(@Nullable DqIssue issue) {
        if (issue == null) {
            return false;
        }
        return isCrossSource(issue)
                ? DqReconciliationFragment.hasReconciliation(issue.getCheckResult())
                : DqViolationsFragment.hasViolations(issue.getCheckResult());
    }

    @Subscribe("dqIssuesDataGrid.violationsAction")
    public void onDqIssuesDataGridViolations(final ActionPerformedEvent event) {
        showViolations(dqIssuesDataGrid.getSingleSelectedItem());
    }

    @Subscribe("dqIssuesTreeDataGrid.violationsAction")
    public void onDqIssuesTreeDataGridViolations(final ActionPerformedEvent event) {
        showViolations(selectedTreeIssue());
    }

    private void showViolations(@Nullable DqIssue issue) {
        if (issue == null || issue.getCheckResult() == null) {
            return;
        }
        if (isCrossSource(issue)) {
            dialogWindows.view(this, DqCheckRunReconciliationView.class)
                    .withViewConfigurer(view -> view.setCheckResult(issue.getCheckResult()))
                    .open();
        } else {
            dialogWindows.view(this, DqCheckRunViolationsView.class)
                    .withViewConfigurer(view -> view.setCheckResult(issue.getCheckResult()))
                    .open();
        }
    }

    private static boolean isCrossSource(DqIssue issue) {
        return issue.getRule() != null && issue.getRule().getRuleType() == DqRuleType.CROSS_SOURCE_AGGREGATED;
    }

    /** Keeps the id of the chosen grouping in the {@value #PARAMETER} URL query parameter. */
    private class GroupingBinder extends AbstractUrlQueryParametersBinder {

        private static final String PARAMETER = "grouping";

        GroupingBinder() {
            groupingField.addValueChangeListener(event -> {
                if (event.isFromClient() && event.getValue() != null) {
                    fireQueryParametersChanged(new UrlQueryParametersFacet.UrlQueryParametersChangeEvent(this,
                            QueryParameters.of(PARAMETER, event.getValue().getId())));
                }
            });
        }

        @Override
        public void updateState(QueryParameters queryParameters) {
            queryParameters.getSingleParameter(PARAMETER)
                    .map(DqIssueGrouping::fromId)
                    .filter(dqIssueGroupingService.availableGroupings()::contains)
                    .ifPresent(groupingField::setValue);
        }

        @Override
        public Component getComponent() {
            return groupingField;
        }
    }
}
