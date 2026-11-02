package uz.kapitalbank.umida.view.report;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.Report;
import io.jmix.reportsflowui.view.report.ReportDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.List;

/**
 * The editor of reports of the add-on with the classification of the report data, next to the add-on's
 * {@code report_Report.detail}. It is opened by {@link ReportCustomListView}.
 *
 * <p>The descriptor extends the one of the add-on and takes its message group, which the inherited
 * layout and the controller rely on. What the controller of the add-on needs stays in the layout,
 * hidden: the group combo box and the name localization.
 *
 * <p>The code is generated on save, see {@link uz.kapitalbank.umida.service.report.ReportCustomEventListener},
 * so a report only shows it once it has been saved, read-only.
 */
@Route(value = "custom-reports/:id", layout = MainView.class)
@ViewController(id = "umida_ReportCustom.detail")
@ViewDescriptor(path = "report-custom-detail-view.xml")
@EditedEntityContainer("reportDc")
public class ReportCustomDetailView extends ReportDetailView {

    @Autowired
    private ReportCustomService reportCustomService;

    @ViewComponent("mainTabSheet.securityTab")
    private Tab securityTab;
    @ViewComponent
    private FormLayout reportForm;
    @ViewComponent
    private TypedTextField<String> codeField;
    @ViewComponent
    private CollectionContainer<DictDataDomain> domainsDc;
    @ViewComponent
    private CollectionContainer<DictDataDomain> secondaryDomainOptionsDc;

    /** Not on init: the buttons of the tab get the actions of its grids after that, and look the grids up in the view. */
    @Subscribe
    public void onViewReady(final ReadyEvent event) {
        mainTabSheet.remove(securityTab);
        reportForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        refreshCodeField();
        refreshSecondaryDomainOptions();
    }

    /** Save without closing: the new report now has a code to show. */
    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        refreshCodeField();
    }

    @Subscribe(id = "reportDc", target = Target.DATA_CONTAINER)
    public void onReportDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<Report> event) {
        if ("domain".equals(event.getProperty())) {
            dropPrimaryDomainFromSecondaryOnes();
            refreshSecondaryDomainOptions();
        }
    }

    /**
     * The code is not typed in, so there is nothing to check. It is assigned here and not left to the event
     * listener, because the add-on serializes the report right after this, and an export takes the code from there.
     */
    @Override
    protected void checkReportCode(BeforeSaveEvent event) {
        if (getEditedEntity().getCode() == null) {
            getEditedEntity().setCode(reportCustomService.generateCode());
        }
    }

    private void refreshCodeField() {
        codeField.setVisible(!entityStates.isNew(getEditedEntity()));
    }

    private void refreshSecondaryDomainOptions() {
        secondaryDomainOptionsDc.setItems(
                reportCustomService.withoutPrimaryDomain(domainsDc.getItems(), getEditedReport().getDomain()));
    }

    private void dropPrimaryDomainFromSecondaryOnes() {
        ReportCustom report = getEditedReport();
        List<DictDataDomain> secondaryDomains = report.getSecondaryDomains();
        if (secondaryDomains != null && secondaryDomains.contains(report.getDomain())) {
            report.setSecondaryDomains(
                    reportCustomService.withoutPrimaryDomain(secondaryDomains, report.getDomain()));
        }
    }

    private ReportCustom getEditedReport() {
        return (ReportCustom) getEditedEntity();
    }
}
