package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.CoreProperties;
import io.jmix.core.LoadContext;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiProperties;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.SecuredBaseAction;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.action.multivaluepicker.MultiValueSelectAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.download.ByteArrayDownloadDataProvider;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.facet.UrlQueryParametersFacet;
import io.jmix.flowui.facet.urlqueryparameters.AbstractUrlQueryParametersBinder;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.HasActions;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.util.RemoveOperation;
import io.jmix.flowui.view.*;
import io.jmix.reports.ReportImportExport;
import io.jmix.reports.entity.Report;
import io.jmix.reports.exception.MissingDefaultTemplateException;
import io.jmix.reportsflowui.ReportsClientProperties;
import io.jmix.reportsflowui.download.ReportDownloader;
import io.jmix.reportsflowui.runner.FluentUiReportRunner;
import io.jmix.reportsflowui.runner.ParametersDialogShowMode;
import io.jmix.reportsflowui.runner.UiReportRunner;
import io.jmix.reportsflowui.view.history.ReportExecutionListView;
import io.jmix.reportsflowui.view.importdialog.ReportImportDialogView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.enums.report.ReportGrouping;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.service.report.ReportGroupingService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The list of reports, next to the add-on's {@code report_Report.list}. It takes nothing from that view: the
 * actions do what its handlers do, while the data comes from a plain JPQL loader, so the header filters of the
 * grid and its sorting work on any attribute. The add-on loads through {@code ReportRepository}, which ignores
 * the conditions of the loader. The loader shows the reports of the database, not the annotated ones.
 * <p>
 * The reports are shown either as one paged list or — when a grouping is chosen — as a tree of all of them
 * under their group or domain, without pages. The tree holds what the paged list selects, its column filters,
 * its order and the "mine" filter included. The buttons act on the report selected in the grid shown; a group
 * row selects no report. The grouping is kept in the URL, so that coming back from a report opened from the
 * tree returns to it.
 */
@Route(value = "custom-reports", layout = MainView.class)
@ViewController(id = "umida_ReportCustom.list")
@ViewDescriptor(path = "report-custom-list-view.xml")
@LookupComponent("reportsDataGrid")
@DialogMode(width = "60em")
public class ReportCustomListView extends StandardListView<Report> {

    private static final String GROUP_LOOKUP_VIEW_ID = "umida_ReportGroupCustom.list";

    /** The column showing the value the reports are grouped by, hidden in the tree: the group row shows it. */
    private static final Map<ReportGrouping, String> GROUPING_COLUMNS = Map.of(
            ReportGrouping.GROUP, "group",
            ReportGrouping.DOMAIN, "domain");

    /** The actions of the tree, all of them acting on the report of the selected row. */
    private static final List<String> TREE_ACTIONS = List.of("edit", "remove", "copy", "export", "runReport", "executions");

    @ViewComponent
    private DataGrid<Report> reportsDataGrid;
    @ViewComponent
    private CollectionLoader<Report> reportsDl;
    @ViewComponent
    private CollectionContainer<Report> reportsDc;
    @ViewComponent
    private TreeDataGrid<ReportTreeItem> reportsTreeDataGrid;
    @ViewComponent
    private CollectionLoader<ReportTreeItem> reportTreeItemsDl;
    @ViewComponent
    private CollectionContainer<ReportTreeItem> reportTreeItemsDc;
    @ViewComponent
    private JmixSelect<ReportGrouping> groupingField;
    @ViewComponent
    private SimplePagination pagination;
    @ViewComponent
    private JmixButton editBtn;
    @ViewComponent
    private JmixButton removeBtn;
    @ViewComponent
    private JmixButton copyBtn;
    @ViewComponent
    private JmixButton exportBtn;
    @ViewComponent
    private JmixButton runReportBtn;
    @ViewComponent
    private JmixButton executionsBtn;

    @Autowired
    private ReportCustomService reportCustomService;
    @Autowired
    private ReportGroupingService reportGroupingService;
    @Autowired
    private ReportImportExport reportImportExport;
    @Autowired
    private ReportDownloader downloader;
    @Autowired
    private UiReportRunner uiReportRunner;
    @Autowired
    private ReportsClientProperties reportsClientProperties;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private RemoveOperation removeOperation;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Messages messages;
    @Autowired
    private MetadataTools metadataTools;
    @Autowired
    private UiProperties uiProperties;
    @Autowired
    private CoreProperties coreProperties;

    /**
     * The "mine" filter: the reports created by the current user. It carries no value while the
     * checkbox is clear, and the loader then skips it.
     */
    private final PropertyCondition mineCondition = PropertyCondition.equal("createdBy", null).skipNullOrEmpty();

    private ReportGrouping grouping = ReportGrouping.ALL;

    /** The groups expanded before the tree is reloaded. */
    private Set<UUID> expandedGroupIds = Set.of();

    @Subscribe
    public void onInit(final InitEvent event) {
        installGroupFilterLookup();
        installMineCondition();

        groupingField.setItems(reportGroupingService.availableGroupings());
        groupingField.setValue(ReportGrouping.ALL);
        ViewControllerUtils.getViewFacet(this, UrlQueryParametersFacet.class)
                .registerBinder(new GroupingBinder());
        TREE_ACTIONS.forEach(id -> ((SecuredBaseAction) reportsTreeDataGrid.getAction(id))
                .addEnabledRule(() -> selectedTreeReport() != null));
    }

    /** Adds the "mine" filter next to the conditions of the column header filters, which share the root condition. */
    private void installMineCondition() {
        Condition root = reportsDl.getCondition();
        LogicalCondition and = root instanceof LogicalCondition logical ? logical : LogicalCondition.and();
        if (root != null && root != and) {
            and.add(root);
        }
        and.add(mineCondition);
        reportsDl.setCondition(and);
    }

    /** The list may be on a page the filtered reports do not reach, so it goes back to the first one. */
    @Subscribe("mineField")
    public void onMineFieldValueChange(final AbstractField.ComponentValueChangeEvent<JmixCheckbox, Boolean> event) {
        mineCondition.setParameterValue(Boolean.TRUE.equals(event.getValue())
                ? currentAuthentication.getUser().getUsername()
                : null);
        reportsDl.setFirstResult(0);
        reload();
    }

    @Subscribe("groupingField")
    public void onGroupingFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<JmixSelect<ReportGrouping>, ReportGrouping> event) {
        if (event.getValue() != null) {
            setGrouping(event.getValue());
        }
    }

    /** Shows the paged list or the tree, binds the buttons to the actions of the grid shown and reloads it. */
    private void setGrouping(ReportGrouping grouping) {
        if (this.grouping == grouping) {
            return;
        }
        this.grouping = grouping;

        boolean grouped = isGrouped();
        reportsDataGrid.setVisible(!grouped);
        pagination.setVisible(!grouped);
        reportsTreeDataGrid.setVisible(grouped);
        bindButtons(grouped ? reportsTreeDataGrid : reportsDataGrid);

        if (grouped) {
            GROUPING_COLUMNS.forEach((columnGrouping, key) -> {
                // a column the user may not see is not in the grid
                DataGridColumn<ReportTreeItem> column = reportsTreeDataGrid.getColumnByKey(key);
                if (column != null) {
                    column.setVisible(columnGrouping != grouping);
                }
            });
        }
        reload();
    }

    private boolean isGrouped() {
        return grouping != ReportGrouping.ALL;
    }

    private void reload() {
        (isGrouped() ? reportTreeItemsDl : reportsDl).load();
    }

    /** The buttons of create and import stay with the paged list: those actions need no row. */
    private void bindButtons(HasActions grid) {
        bindButton(editBtn, grid, "edit");
        bindButton(removeBtn, grid, "remove");
        bindButton(copyBtn, grid, "copy");
        bindButton(exportBtn, grid, "export");
        bindButton(runReportBtn, grid, "runReport");
        bindButton(executionsBtn, grid, "executions");
    }

    /** The buttons keep their own look: icon only, with the title as the tooltip. */
    private static void bindButton(JmixButton button, HasActions grid, String actionId) {
        button.setAction(grid.getAction(actionId), false);
        button.setText("");
    }

    @Install(to = "reportTreeItemsDl", target = Target.DATA_LOADER)
    private List<ReportTreeItem> reportTreeItemsDlLoadDelegate(final LoadContext<ReportTreeItem> loadContext) {
        return reportGroupingService.loadTree(reportsDl.createLoadContext(), grouping);
    }

    /** A reload brings new rows: the groups that were expanded are expanded again. */
    @Subscribe(id = "reportTreeItemsDl", target = Target.DATA_LOADER)
    public void onReportTreeItemsDlPreLoad(final CollectionLoader.PreLoadEvent<ReportTreeItem> event) {
        expandedGroupIds = reportTreeItemsDc.getItems().stream()
                .filter(item -> item.isGroup() && reportsTreeDataGrid.isExpanded(item))
                .map(ReportTreeItem::getId)
                .collect(Collectors.toSet());
    }

    @Subscribe(id = "reportTreeItemsDl", target = Target.DATA_LOADER)
    public void onReportTreeItemsDlPostLoad(final CollectionLoader.PostLoadEvent<ReportTreeItem> event) {
        reportsTreeDataGrid.expand(event.getLoadedEntities().stream()
                .filter(item -> expandedGroupIds.contains(item.getId()))
                .toList());
    }

    /** A group opens and closes on a double click, a report opens. */
    @Subscribe("reportsTreeDataGrid")
    public void onReportsTreeDataGridItemDoubleClick(final ItemDoubleClickEvent<ReportTreeItem> event) {
        ReportTreeItem item = event.getItem();
        if (item == null) {
            return;
        }
        if (!item.isGroup()) {
            edit(item.getReport());
        } else if (reportsTreeDataGrid.isExpanded(item)) {
            reportsTreeDataGrid.collapse(item);
        } else {
            reportsTreeDataGrid.expand(item);
        }
    }

    /** The loaded report of the selected tree row: after a reload the grid may still hold the previous one. None for a group row. */
    @Nullable
    private Report selectedTreeReport() {
        ReportTreeItem selected = reportsTreeDataGrid.getSingleSelectedItem();
        ReportTreeItem loaded = selected != null ? reportTreeItemsDc.getItemOrNull(selected.getId()) : null;
        return loaded != null ? loaded.getReport() : null;
    }

    /**
     * The filter of the group column picks from our list of groups: the entity keeps the list of the add-on as
     * its lookup. A change of the operation can generate a new field, so the view is set on it again. The column
     * is absent if the security hides it.
     */
    private void installGroupFilterLookup() {
        var groupColumn = reportsDataGrid.getColumnByKey("group");
        if (groupColumn != null && groupColumn.getHeaderComponent() instanceof DataGridHeaderFilter headerFilter) {
            PropertyFilter<?> groupFilter = headerFilter.getPropertyFilter();
            setGroupLookupView(groupFilter);
            groupFilter.addOperationChangeListener(e -> setGroupLookupView(groupFilter));
        }
    }

    /** The field of a single value and the one of a list open the lookup through different actions. */
    private void setGroupLookupView(PropertyFilter<?> groupFilter) {
        if (groupFilter.getValueComponent() instanceof HasActions field) {
            if (field.getAction(EntityLookupAction.ID) instanceof EntityLookupAction<?> lookup) {
                lookup.setViewId(GROUP_LOOKUP_VIEW_ID);
            }
            if (field.getAction(MultiValueSelectAction.ID) instanceof MultiValueSelectAction<?> select) {
                select.setLookupViewId(GROUP_LOOKUP_VIEW_ID);
            }
        }
    }

    /** The localized name, as the add-on shows it. */
    @Supply(to = "reportsDataGrid.name", subject = "renderer")
    private Renderer<Report> reportsDataGridNameRenderer() {
        return new TextRenderer<>(metadataTools::getInstanceName);
    }

    @Subscribe("reportsDataGrid.copy")
    public void onReportsDataGridCopy(final ActionPerformedEvent event) {
        copy(reportsDataGrid.getSingleSelectedItem());
    }

    @Subscribe("reportsTreeDataGrid.copy")
    public void onReportsTreeDataGridCopy(final ActionPerformedEvent event) {
        copy(selectedTreeReport());
    }

    private void copy(@Nullable Report report) {
        if (report == null) {
            return;
        }
        reportCustomService.copy(report);
        reload();
    }

    @Subscribe("reportsDataGrid.importAction")
    public void onReportsDataGridImportAction(final ActionPerformedEvent event) {
        dialogWindows.view(this, ReportImportDialogView.class)
                .withAfterCloseListener(e -> {
                    if (e.closedWith(StandardOutcome.SAVE)) {
                        reload();
                    }
                })
                .build()
                .open();
    }

    @Subscribe("reportsDataGrid.export")
    public void onReportsDataGridExport(final ActionPerformedEvent event) {
        export(reportsDataGrid.getSingleSelectedItem());
    }

    @Subscribe("reportsTreeDataGrid.export")
    public void onReportsTreeDataGridExport(final ActionPerformedEvent event) {
        export(selectedTreeReport());
    }

    private void export(@Nullable Report report) {
        if (report == null) {
            return;
        }
        ByteArrayDownloadDataProvider provider = new ByteArrayDownloadDataProvider(
                reportImportExport.exportReports(List.of(report)),
                uiProperties.getSaveExportedByteArrayDataThresholdBytes(),
                coreProperties.getTempDir());
        downloader.download(provider, report.getName(), DownloadFormat.ZIP);
    }

    @Subscribe("reportsDataGrid.runReport")
    public void onReportsDataGridRunReport(final ActionPerformedEvent event) {
        runReport(reportsDataGrid.getSingleSelectedItem());
    }

    @Subscribe("reportsTreeDataGrid.runReport")
    public void onReportsTreeDataGridRunReport(final ActionPerformedEvent event) {
        runReport(selectedTreeReport());
    }

    private void runReport(@Nullable Report report) {
        if (report == null) {
            return;
        }

        FluentUiReportRunner fluentRunner = uiReportRunner.byReportEntity(report)
                .withParametersDialogShowMode(ParametersDialogShowMode.IF_REQUIRED);
        try {
            if (reportsClientProperties.getUseBackgroundReportProcessing()) {
                fluentRunner.inBackground(this);
            }
            fluentRunner.runAndShow();
        } catch (MissingDefaultTemplateException e) {
            notifications.create(
                            messages.getMessage("runningReportError.title"),
                            messages.getMessage("missingDefaultTemplateError.description"))
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("reportsDataGrid.executions")
    public void onReportsDataGridExecutions(final ActionPerformedEvent event) {
        showExecutions(reportsDataGrid.getSingleSelectedItem());
    }

    @Subscribe("reportsTreeDataGrid.executions")
    public void onReportsTreeDataGridExecutions(final ActionPerformedEvent event) {
        showExecutions(selectedTreeReport());
    }

    private void showExecutions(@Nullable Report report) {
        if (report == null) {
            return;
        }
        DialogWindow<ReportExecutionListView> dialog = dialogWindows.view(this, ReportExecutionListView.class)
                .build();
        dialog.getView().setFilterByReports(List.of(report));
        dialog.open();
    }

    /** What the edit action of the paged list does, which works on entities of a grid: the editor, coming back here. */
    @Subscribe("reportsTreeDataGrid.edit")
    public void onReportsTreeDataGridEdit(final ActionPerformedEvent event) {
        edit(selectedTreeReport());
    }

    private void edit(@Nullable Report report) {
        if (report == null) {
            return;
        }
        viewNavigators.detailView(this, Report.class)
                .withViewClass(ReportCustomDetailView.class)
                .editEntity(report)
                .withBackwardNavigation(true)
                .navigate();
    }

    /** What the remove action of the paged list does, which works on entities of a grid: with its confirmation. */
    @Subscribe("reportsTreeDataGrid.remove")
    public void onReportsTreeDataGridRemove(final ActionPerformedEvent event) {
        Report report = selectedTreeReport();
        if (report == null) {
            return;
        }
        removeOperation.builder(Report.class, this)
                .withContainer(reportsDc)
                .withItems(List.of(report))
                .afterActionPerformed(e -> reportTreeItemsDl.load())
                .remove();
    }

    @Install(to = "reportsDataGrid.copy", subject = "enabledRule")
    private boolean reportsDataGridCopyEnabledRule() {
        return reportCustomService.isCreatePermitted();
    }

    @Install(to = "reportsTreeDataGrid.copy", subject = "enabledRule")
    private boolean reportsTreeDataGridCopyEnabledRule() {
        return reportCustomService.isCreatePermitted();
    }

    @Install(to = "reportsDataGrid.importAction", subject = "enabledRule")
    private boolean reportsDataGridImportActionEnabledRule() {
        return reportCustomService.isCreatePermitted();
    }

    @Install(to = "reportsTreeDataGrid.remove", subject = "enabledRule")
    private boolean reportsTreeDataGridRemoveEnabledRule() {
        return reportCustomService.isDeletePermitted();
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
                    .map(ReportGrouping::fromId)
                    .filter(reportGroupingService.availableGroupings()::contains)
                    .ifPresent(groupingField::setValue);
        }

        @Override
        public Component getComponent() {
            return groupingField;
        }
    }
}
