package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ItemDoubleClickEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.dom.Style;
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
import io.jmix.flowui.kit.action.Action;
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
import uz.kapitalbank.umida.component.reportgroup.ReportTile;
import uz.kapitalbank.umida.dto.report.ReportTreeItem;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.entity.report.ReportGroupCustom;
import uz.kapitalbank.umida.enums.report.ReportGrouping;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.service.report.ReportFolderService;
import uz.kapitalbank.umida.service.report.ReportGroupCustomService;
import uz.kapitalbank.umida.service.report.ReportGroupingService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <p>
 * The buttons of the top right corner show the reports either as the list — paged or grouped — or as tiles,
 * folders and files the way a file system shows them: the report groups nested by their parent, then the
 * reports of the group. A click selects a tile, a double click opens the folder or the report; the buttons
 * act on the report of the selected tile. The tiles hold the reports the "mine" filter selects, the
 * filters of the columns stay with the list. The tiles and the folder shown are kept in the URL as well.
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

    /** The view actions of the tiles, all of them acting on the report of the selected tile: {@code tile} and the id of the grid action. */
    private static final List<String> TILE_ACTIONS = TREE_ACTIONS.stream().map(ReportCustomListView::tileActionId).toList();

    /** The icon of a report tile, of the "umida-icons" iconset. */
    private static final String REPORT_TILE_ICON = "reports";

    private static final String ACTIVE_CLASS_NAME = "active";

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
    @ViewComponent
    private JmixCheckbox mineField;
    @ViewComponent
    private JmixButton listModeBtn;
    @ViewComponent
    private JmixButton tilesModeBtn;
    @ViewComponent
    private HorizontalLayout folderPanel;
    @ViewComponent
    private JmixButton folderBackBtn;
    @ViewComponent
    private JmixButton folderUpBtn;
    @ViewComponent
    private Span folderPath;
    @ViewComponent
    private Div tilesBox;
    @ViewComponent
    private Span tilesEmpty;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private ReportCustomService reportCustomService;
    @Autowired
    private ReportGroupingService reportGroupingService;
    @Autowired
    private ReportFolderService reportFolderService;
    @Autowired
    private ReportGroupCustomService reportGroupCustomService;
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

    private final TilesBinder tilesBinder = new TilesBinder();

    private boolean tilesShown;

    /** The folder shown as tiles; none while the tiles are not shown yet. */
    @Nullable
    private ReportFolderService.Folder folder;

    /** The tiles of the folder shown by the id of their group or report. */
    private final Map<UUID, ReportTile> tiles = new LinkedHashMap<>();

    /** The reports of the folder shown by id. */
    private final Map<UUID, ReportCustom> tileReports = new LinkedHashMap<>();

    /** The id of the group or the report of the selected tile. */
    @Nullable
    private UUID selectedTileId;

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

        ViewControllerUtils.getViewFacet(this, UrlQueryParametersFacet.class).registerBinder(tilesBinder);
        TILE_ACTIONS.forEach(id -> ((SecuredBaseAction) getViewActions().getAction(id))
                .addEnabledRule(() -> selectedTileReport() != null));
        ((SecuredBaseAction) getViewActions().getAction(tileActionId("copy")))
                .addEnabledRule(reportCustomService::isCreatePermitted);
        ((SecuredBaseAction) getViewActions().getAction(tileActionId("remove")))
                .addEnabledRule(reportCustomService::isDeletePermitted);
        refreshTileActions();
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

    /** Shows the paged list or the tree, unless the tiles are shown, and reloads what is shown. */
    private void setGrouping(ReportGrouping grouping) {
        if (this.grouping == grouping) {
            return;
        }
        this.grouping = grouping;

        if (isGrouped()) {
            GROUPING_COLUMNS.forEach((columnGrouping, key) -> {
                // a column the user may not see is not in the grid
                DataGridColumn<ReportTreeItem> column = reportsTreeDataGrid.getColumnByKey(key);
                if (column != null) {
                    column.setVisible(columnGrouping != grouping);
                }
            });
        }
        updateLayout();
        reload();
    }

    private boolean isGrouped() {
        return grouping != ReportGrouping.ALL;
    }

    /** Shows the list or the tiles and reloads what is shown; the list keeps its grouping. */
    private void setTilesShown(boolean tilesShown) {
        if (this.tilesShown == tilesShown) {
            return;
        }
        this.tilesShown = tilesShown;
        updateLayout();
        reload();
    }

    /** Shows the paged list, the tree or the tiles and binds the buttons to the actions of what is shown. */
    private void updateLayout() {
        boolean grouped = isGrouped();
        reportsDataGrid.setVisible(!tilesShown && !grouped);
        setPaginationShown(!tilesShown && !grouped);
        reportsTreeDataGrid.setVisible(!tilesShown && grouped);
        groupingField.setVisible(!tilesShown);
        folderPanel.setVisible(tilesShown);
        tilesBox.setVisible(tilesShown);
        tilesEmpty.setVisible(tilesShown && folder != null && folder.isEmpty());
        listModeBtn.setClassName(ACTIVE_CLASS_NAME, !tilesShown);
        tilesModeBtn.setClassName(ACTIVE_CLASS_NAME, tilesShown);

        if (tilesShown) {
            bindButtons(id -> getViewActions().getAction(tileActionId(id)));
        } else {
            bindButtons((grouped ? reportsTreeDataGrid : reportsDataGrid)::getAction);
        }
    }

    /**
     * The pagination is hidden by its style, not made invisible: Vaadin holds back the JavaScript of an invisible
     * component until it is shown again — here the one each load of the list runs on the navigation buttons, even
     * while the tree or the tiles are shown — and the browser then answers it for the buttons still disabled,
     * which the server ignores with a "disabled return channel" warning each.
     */
    private void setPaginationShown(boolean shown) {
        if (shown) {
            pagination.getStyle().remove("display");
        } else {
            pagination.getStyle().setDisplay(Style.Display.NONE);
        }
    }

    private void reload() {
        if (tilesShown) {
            showFolder(folder != null ? folderId() : tilesBinder.initialFolderId);
        } else {
            (isGrouped() ? reportTreeItemsDl : reportsDl).load();
        }
    }

    /** The buttons of create and import stay with the paged list: those actions need no row. */
    private void bindButtons(Function<String, Action> actions) {
        bindButton(editBtn, actions.apply("edit"));
        bindButton(removeBtn, actions.apply("remove"));
        bindButton(copyBtn, actions.apply("copy"));
        bindButton(exportBtn, actions.apply("export"));
        bindButton(runReportBtn, actions.apply("runReport"));
        bindButton(executionsBtn, actions.apply("executions"));
    }

    /** The buttons keep their own look: icon only, with the title as the tooltip. */
    private static void bindButton(JmixButton button, Action action) {
        button.setAction(action, false);
        button.setText("");
    }

    private static String tileActionId(String gridActionId) {
        return "tile" + Character.toUpperCase(gridActionId.charAt(0)) + gridActionId.substring(1);
    }

    @Subscribe(id = "listModeBtn", subject = "clickListener")
    public void onListModeBtnClick(final ClickEvent<JmixButton> event) {
        setTilesShown(false);
        tilesBinder.fireChanged();
    }

    @Subscribe(id = "tilesModeBtn", subject = "clickListener")
    public void onTilesModeBtnClick(final ClickEvent<JmixButton> event) {
        setTilesShown(true);
        tilesBinder.fireChanged();
    }

    /** One level up, with the folder left selected. */
    @Subscribe(id = "folderBackBtn", subject = "clickListener")
    public void onFolderBackBtnClick(final ClickEvent<JmixButton> event) {
        if (folder != null && !folder.isTop()) {
            ReportGroupCustom parent = folder.getParent();
            openFolder(parent != null ? parent.getId() : null, folderId());
        }
    }

    /** The top level, with the folder the shown one lies in selected. */
    @Subscribe(id = "folderUpBtn", subject = "clickListener")
    public void onFolderUpBtnClick(final ClickEvent<JmixButton> event) {
        if (folder != null && !folder.isTop()) {
            openFolder(null, folder.path().get(0).getId());
        }
    }

    @Nullable
    private UUID folderId() {
        return folder == null || folder.isTop() ? null : folder.group().getId();
    }

    /** Shows the folder, the tile of {@code selectedId} selected, and keeps the folder in the URL. */
    private void openFolder(@Nullable UUID groupId, @Nullable UUID selectedId) {
        selectedTileId = selectedId;
        showFolder(groupId);
        tilesBinder.fireChanged();
    }

    /**
     * Shows the folder as tiles, the groups first, then the reports; the top level if the group is no longer
     * there. A tile selected before stays selected if it is still there.
     */
    private void showFolder(@Nullable UUID groupId) {
        folder = reportFolderService.loadFolder(groupId, Boolean.TRUE.equals(mineField.getValue()));

        tilesBox.removeAll();
        tiles.clear();
        tileReports.clear();
        for (ReportGroupCustom group : folder.groups()) {
            addTile(group.getId(), new ReportTile(
                    metadataTools.getInstanceName(group),
                    reportGroupCustomService.getIconOrDefault(group.getIcon()),
                    reportGroupCustomService.getColorOrDefault(group.getColor()),
                    ""));
        }
        for (ReportCustom report : folder.reports()) {
            tileReports.put(report.getId(), report);
            addTile(report.getId(), new ReportTile(
                    metadataTools.getInstanceName(report),
                    REPORT_TILE_ICON,
                    ReportGroupCustomService.FOLDER_COLOR,
                    "report-file"));
        }
        selectTile(selectedTileId != null && tiles.containsKey(selectedTileId) ? selectedTileId : null);

        folderBackBtn.setEnabled(!folder.isTop());
        folderUpBtn.setEnabled(!folder.isTop());
        folderPath.setText(folderPathText(folder));
        tilesEmpty.setVisible(tilesShown && folder.isEmpty());
    }

    /** A click selects the tile, a double click opens the folder or the report. */
    private void addTile(UUID id, ReportTile tile) {
        tile.addClickListener(event -> {
            if (event.getClickCount() > 1) {
                openTile(id);
            } else {
                selectTile(id);
            }
        });
        tiles.put(id, tile);
        tilesBox.add(tile);
    }

    private void openTile(UUID id) {
        ReportCustom report = tileReports.get(id);
        if (report != null) {
            edit(report);
        } else {
            openFolder(id, null);
        }
    }

    private void selectTile(@Nullable UUID id) {
        selectedTileId = id;
        tiles.forEach((tileId, tile) -> tile.setSelected(tileId.equals(id)));
        refreshTileActions();
    }

    private void refreshTileActions() {
        TILE_ACTIONS.forEach(id -> getViewActions().getAction(id).refreshState());
    }

    /** The report of the selected tile; none for the tile of a group. */
    @Nullable
    private ReportCustom selectedTileReport() {
        return selectedTileId != null ? tileReports.get(selectedTileId) : null;
    }

    private String folderPathText(ReportFolderService.Folder folder) {
        return Stream.concat(
                        Stream.of(messageBundle.getMessage("folderPath.top")),
                        folder.path().stream().map(metadataTools::getInstanceName))
                .collect(Collectors.joining(" / "));
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

    @Subscribe("reportsTreeDataGrid.remove")
    public void onReportsTreeDataGridRemove(final ActionPerformedEvent event) {
        remove(selectedTreeReport());
    }

    /** What the remove action of the paged list does, which works on entities of a grid: with its confirmation. */
    private void remove(@Nullable Report report) {
        if (report == null) {
            return;
        }
        removeOperation.builder(Report.class, this)
                .withContainer(reportsDc)
                .withItems(List.of(report))
                .afterActionPerformed(e -> reload())
                .remove();
    }

    @Subscribe("tileEdit")
    public void onTileEdit(final ActionPerformedEvent event) {
        edit(selectedTileReport());
    }

    @Subscribe("tileRemove")
    public void onTileRemove(final ActionPerformedEvent event) {
        remove(selectedTileReport());
    }

    @Subscribe("tileCopy")
    public void onTileCopy(final ActionPerformedEvent event) {
        copy(selectedTileReport());
    }

    @Subscribe("tileExport")
    public void onTileExport(final ActionPerformedEvent event) {
        export(selectedTileReport());
    }

    @Subscribe("tileRunReport")
    public void onTileRunReport(final ActionPerformedEvent event) {
        runReport(selectedTileReport());
    }

    @Subscribe("tileExecutions")
    public void onTileExecutions(final ActionPerformedEvent event) {
        showExecutions(selectedTileReport());
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

    /**
     * Keeps the tiles in the {@value #MODE} URL query parameter and the folder shown in the {@value #FOLDER}
     * one; the list leaves out both.
     */
    private class TilesBinder extends AbstractUrlQueryParametersBinder {

        private static final String MODE = "mode";
        private static final String TILES = "tiles";
        private static final String FOLDER = "folder";

        /** The folder of the URL, shown once the tiles are. */
        @Nullable
        private UUID initialFolderId;

        void fireChanged() {
            UUID folderId = tilesShown ? folderId() : null;
            fireQueryParametersChanged(new UrlQueryParametersFacet.UrlQueryParametersChangeEvent(this,
                    new QueryParameters(Map.of(
                            MODE, tilesShown ? List.of(TILES) : List.of(),
                            FOLDER, folderId != null ? List.of(folderId.toString()) : List.of()))));
        }

        @Override
        public void updateState(QueryParameters queryParameters) {
            boolean tiles = queryParameters.getSingleParameter(MODE).filter(TILES::equals).isPresent();
            UUID folderId = queryParameters.getSingleParameter(FOLDER).flatMap(TilesBinder::parseUuid).orElse(null);
            if (!tiles) {
                setTilesShown(false);
            } else if (!tilesShown) {
                initialFolderId = folderId;
                setTilesShown(true);
            } else if (!Objects.equals(folderId, folderId())) {
                selectedTileId = null;
                showFolder(folderId);
            }
        }

        private static Optional<UUID> parseUuid(String value) {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        @Override
        public Component getComponent() {
            return tilesBox;
        }
    }
}
