package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.CoreProperties;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiProperties;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.action.multivaluepicker.MultiValueSelectAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.headerfilter.DataGridHeaderFilter;
import io.jmix.flowui.component.propertyfilter.PropertyFilter;
import io.jmix.flowui.download.ByteArrayDownloadDataProvider;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.HasActions;
import io.jmix.flowui.model.CollectionLoader;
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
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of reports, next to the add-on's {@code report_Report.list}. It takes nothing from that view: the
 * actions do what its handlers do, while the data comes from a plain JPQL loader, so the header filters of the
 * grid and its sorting work on any attribute. The add-on loads through {@code ReportRepository}, which ignores
 * the conditions of the loader. The loader shows the reports of the database, not the annotated ones.
 */
@Route(value = "custom-reports", layout = MainView.class)
@ViewController(id = "umida_ReportCustom.list")
@ViewDescriptor(path = "report-custom-list-view.xml")
@LookupComponent("reportsDataGrid")
@DialogMode(width = "60em")
public class ReportCustomListView extends StandardListView<Report> {

    private static final String GROUP_LOOKUP_VIEW_ID = "umida_ReportGroupCustom.list";

    @ViewComponent
    private DataGrid<Report> reportsDataGrid;
    @ViewComponent
    private CollectionLoader<Report> reportsDl;

    @Autowired
    private ReportCustomService reportCustomService;
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
     * The filter of the group column picks from our list of groups: the entity keeps the list of the add-on as
     * its lookup. A change of the operation can generate a new field, so the view is set on it again. The column
     * is absent if the security hides it.
     */
    @Subscribe
    public void onInit(final InitEvent event) {
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
        Report report = reportsDataGrid.getSingleSelectedItem();
        if (report == null) {
            return;
        }
        reportCustomService.copy(report);
        reportsDl.load();
    }

    @Subscribe("reportsDataGrid.importAction")
    public void onReportsDataGridImportAction(final ActionPerformedEvent event) {
        dialogWindows.view(this, ReportImportDialogView.class)
                .withAfterCloseListener(e -> {
                    if (e.closedWith(StandardOutcome.SAVE)) {
                        reportsDl.load();
                    }
                })
                .build()
                .open();
    }

    @Subscribe("reportsDataGrid.export")
    public void onReportsDataGridExport(final ActionPerformedEvent event) {
        Report report = reportsDataGrid.getSingleSelectedItem();
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
        Report report = reportsDataGrid.getSingleSelectedItem();
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
        DialogWindow<ReportExecutionListView> dialog = dialogWindows.view(this, ReportExecutionListView.class)
                .build();
        dialog.getView().setFilterByReports(new ArrayList<>(reportsDataGrid.getSelectedItems()));
        dialog.open();
    }

    @Install(to = "reportsDataGrid.copy", subject = "enabledRule")
    private boolean reportsDataGridCopyEnabledRule() {
        return reportCustomService.isCreatePermitted();
    }

    @Install(to = "reportsDataGrid.importAction", subject = "enabledRule")
    private boolean reportsDataGridImportActionEnabledRule() {
        return reportCustomService.isCreatePermitted();
    }
}
