package uz.kapitalbank.umida.view.report;

import com.google.common.base.Strings;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.DataGridColumn;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.entitypicker.EntityLookupAction;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportInputParameter;
import io.jmix.reports.entity.ReportValueFormat;
import io.jmix.reportsflowui.view.report.ReportDetailView;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.dict.DictDataDomain;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.service.report.ReportCustomService;
import uz.kapitalbank.umida.service.report.ReportOwnerMemberService;
import uz.kapitalbank.umida.service.report.ReportSqlEnumerationService;
import uz.kapitalbank.umida.view.main.MainView;
import uz.kapitalbank.umida.view.report.access.ReportOwnerMembersFragment;
import uz.kapitalbank.umida.view.report.access.SubdivisionCaptions;

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
 *
 * <p>The tab Business owner holds the owner subdivision and its list, {@link ReportOwnerMembersFragment}: the
 * employees of the owner and of the subdivisions below it, ticked to run the report and to manage the access
 * to it. Picking another owner ticks everyone to run; the list is saved after the report, see
 * {@link #onAfterSave}: whole for a new owner or a new report, otherwise the rows changed only, as the head
 * and the delegates change the list too, on the tab Owners of the access management. The changes of the
 * ticks are not in the data context, so {@link #hasUnsavedChanges()} counts them itself.
 */
@Route(value = "custom-reports/:id", layout = MainView.class)
@ViewController(id = "umida_ReportCustom.detail")
@ViewDescriptor(path = "report-custom-detail-view.xml")
@EditedEntityContainer("reportDc")
public class ReportCustomDetailView extends ReportDetailView {

    @Autowired
    private ReportCustomService reportCustomService;
    @Autowired
    private ReportSqlEnumerationService sqlEnumerationService;

    @ViewComponent("mainTabSheet.securityTab")
    private Tab securityTab;
    @ViewComponent
    private FormLayout reportForm;
    @ViewComponent
    private TypedTextField<String> codeField;
    @ViewComponent
    private DataGrid<ReportValueFormat> valuesFormatsDataGrid;
    @ViewComponent
    private CollectionContainer<DictDataDomain> domainsDc;
    @ViewComponent
    private CollectionContainer<DictDataDomain> secondaryDomainOptionsDc;
    @ViewComponent
    private EntityPicker<OrgStructureSubdivision> businessOwnerField;
    @ViewComponent("businessOwnerField.entityLookupAction")
    private EntityLookupAction<OrgStructureSubdivision> businessOwnerLookupAction;
    @ViewComponent
    private ReportOwnerMembersFragment ownerMembersFragment;
    @Autowired
    private ReportOwnerMemberService ownerMemberService;
    @Autowired
    private Messages messages;
    @Autowired
    private SubdivisionCaptions subdivisionCaptions;

    /** The owner was just picked: its list is a change. */
    private boolean ownerMembersChanged;
    /** The list is to be saved whole: the owner was just picked, or the report is new. */
    private boolean ownerMembersReplaced;

    /** Not on init: the buttons of the tab get the actions of its grids after that, and look the grids up in the view. */
    @Subscribe
    public void onViewReady(final ReadyEvent event) {
        mainTabSheet.remove(securityTab);
        reportForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        moveActionTextsToTitles();
        showCustomParameterTypes();
        refreshCodeField();
        refreshSecondaryDomainOptions();
        ownerMembersFragment.setEditable(!isReadOnly());
        loadOwnerMembers(false);
    }

    /** The owner shows with its parent, as the names of the subdivisions repeat; an inactive one is refused. */
    @Subscribe
    public void onBusinessOwnerInit(final InitEvent event) {
        businessOwnerField.setFormatter(subdivision -> subdivision != null ? subdivisionCaptions.caption(subdivision) : "");
        businessOwnerLookupAction.setSelectValidator(context -> {
            boolean active = context.getSelectedItems().stream().allMatch(ReportCustomDetailView::isActive);
            if (!active) {
                notifications.create(ownerMessage("businessOwnerInactive.text"))
                        .withType(Notifications.Type.WARNING)
                        .show();
            }
            return active;
        });
    }

    /**
     * Save without closing: the new report now has a code to show. The owner list goes after the report,
     * which has to have its owner saved first.
     */
    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        refreshCodeField();
        if (ownerMembersReplaced) {
            ownerMemberService.save(getEditedReport(), ownerMembersFragment.getItems());
        } else {
            ownerMemberService.saveChanges(getEditedReport(), ownerMembersFragment.getChangedItems());
        }
        loadOwnerMembers(false);
    }

    @Override
    public boolean hasUnsavedChanges() {
        return super.hasUnsavedChanges() || ownerMembersChanged || ownerMembersFragment.hasChanges();
    }

    @Subscribe(id = "reportDc", target = Target.DATA_CONTAINER)
    public void onReportDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<Report> event) {
        if ("domain".equals(event.getProperty())) {
            dropPrimaryDomainFromSecondaryOnes();
            refreshSecondaryDomainOptions();
        } else if ("businessOwner".equals(event.getProperty())) {
            loadOwnerMembers(true);
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

    /**
     * Icon-only buttons in the tabs Parameters, Value formats and Templates, as the add-on already has in Bands.
     * The buttons have no id, so this goes through their actions: a button takes its text from the action's
     * text and its title from the action's description.
     */
    private void moveActionTextsToTitles() {
        for (DataGrid<?> grid : List.of(inputParametersDataGrid, valuesFormatsDataGrid, templatesDataGrid)) {
            for (Action action : grid.getActions()) {
                if (!Strings.isNullOrEmpty(action.getText())) {
                    action.setDescription(action.getText());
                    action.setText("");
                }
            }
        }
    }

    /** A parameter of a custom type is stored with its base type, which the column would show otherwise. */
    private void showCustomParameterTypes() {
        DataGridColumn<ReportInputParameter> typeColumn = inputParametersDataGrid.getColumnByKey("type");
        if (typeColumn != null) {
            typeColumn.setRenderer(new TextRenderer<>(sqlEnumerationService::getTypeCaption));
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

    /**
     * The rows of the owner list for the owner the report has now.
     *
     * @param defaults the owner was just picked, see {@link ReportOwnerMemberService#items}
     */
    private void loadOwnerMembers(boolean defaults) {
        ReportCustom report = getEditedReport();
        boolean saved = !entityStates.isNew(report);
        ownerMembersFragment.setItems(ownerMemberService.items(report, report.getBusinessOwner(), defaults || !saved));
        ownerMembersChanged = defaults;
        ownerMembersReplaced = defaults || !saved;
    }

    private static boolean isActive(OrgStructureSubdivision subdivision) {
        return subdivision.getIsActive() == null || subdivision.getIsActive() == 1;
    }

    private String ownerMessage(String key) {
        return messages.getMessage("uz.kapitalbank.umida.view.report", key);
    }

    private ReportCustom getEditedReport() {
        return (ReportCustom) getEditedEntity();
    }
}
