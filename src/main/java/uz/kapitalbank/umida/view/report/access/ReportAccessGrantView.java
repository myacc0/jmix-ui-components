package uz.kapitalbank.umida.view.report.access;

import com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent;
import com.vaadin.flow.component.ClickEvent;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import uz.kapitalbank.umida.dto.report.ReportOwnerMemberItem;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.entity.report.ReportCustom;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantSource;
import uz.kapitalbank.umida.service.report.ReportAccessException;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.service.report.ReportAccessService.GrantResult;
import uz.kapitalbank.umida.service.report.ReportOwnerMemberService;

import java.time.LocalDate;
import java.util.List;

/**
 * Gives the access to run a report without a request: to one employee, or to the employees of a subdivision
 * and of the subdivisions below it, all of them ticked at first. Opened as a dialog from
 * {@link ReportAccessManagementView}; closes with {@link StandardOutcome#SAVE} once the grants are given.
 * The rules — who may give access to which report, who is passed over — are {@link ReportAccessService#grant}'s.
 */
@ViewController(id = "umida_ReportAccessGrantView")
@ViewDescriptor(path = "report-access-grant-view.xml")
@DialogMode(width = "56em")
public class ReportAccessGrantView extends StandardView {

    /** The two ways of giving access, the items of {@code targetField}. */
    private static final String TO_EMPLOYEE = "employee";
    private static final String TO_SUBDIVISION = "subdivision";

    @ViewComponent
    private CollectionContainer<ReportCustom> reportsDc;
    @ViewComponent
    private CollectionContainer<ReportOwnerMemberItem> employeesDc;
    @ViewComponent
    private EntityComboBox<ReportCustom> reportField;
    @ViewComponent
    private JmixRadioButtonGroup<String> targetField;
    @ViewComponent
    private EntityComboBox<OrgStructureEmployee> employeeField;
    @ViewComponent
    private EntityComboBox<OrgStructureSubdivision> subdivisionField;
    @ViewComponent
    private TypedDatePicker<LocalDate> validToField;
    @ViewComponent
    private DataGrid<ReportOwnerMemberItem> employeesDataGrid;
    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private ReportAccessService accessService;
    @Autowired
    private ReportOwnerMemberService ownerMemberService;
    @Autowired
    private SubdivisionCaptions subdivisionCaptions;
    @Autowired
    private Notifications notifications;

    @Nullable
    private ReportCustom initialReport;

    /** The report to give access to, picked beforehand; the user may pick another one. */
    public void setReport(@Nullable ReportCustom report) {
        this.initialReport = report;
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        targetField.setItems(TO_EMPLOYEE, TO_SUBDIVISION);
        targetField.setItemLabelGenerator(target -> messageBundle.getMessage("targetField." + target));
        targetField.setValue(TO_EMPLOYEE);
        subdivisionField.setItemLabelGenerator(subdivisionCaptions::caption);
        validToField.setMin(LocalDate.now());
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        reportsDc.setItems(accessService.managedReports());
        if (initialReport != null) {
            reportsDc.getItems().stream()
                    .filter(report -> report.getId().equals(initialReport.getId()))
                    .findFirst()
                    .ifPresent(reportField::setValue);
        }
    }

    @Subscribe("targetField")
    public void onTargetFieldValueChange(final ComponentValueChangeEvent<JmixRadioButtonGroup<String>, String> event) {
        boolean toSubdivision = TO_SUBDIVISION.equals(event.getValue());
        employeeField.setVisible(!toSubdivision);
        subdivisionField.setVisible(toSubdivision);
        employeesDataGrid.setVisible(toSubdivision);
    }

    /** The employees of the subdivision and below it, all ticked: the user unticks those left out. */
    @Subscribe("subdivisionField")
    public void onSubdivisionFieldValueChange(
            final ComponentValueChangeEvent<EntityComboBox<OrgStructureSubdivision>, OrgStructureSubdivision> event) {
        List<ReportOwnerMemberItem> employees = event.getValue() != null
                ? ownerMemberService.employeesOf(event.getValue())
                : List.of();
        employeesDc.setItems(employees);
        employeesDataGrid.select(employees);
    }

    @Subscribe(id = "saveBtn", subject = "clickListener")
    public void onSaveBtnClick(final ClickEvent<JmixButton> event) {
        ReportCustom report = reportField.getValue();
        boolean toSubdivision = TO_SUBDIVISION.equals(targetField.getValue());
        List<OrgStructureEmployee> employees = toSubdivision
                ? employeesDataGrid.getSelectedItems().stream().map(ReportOwnerMemberItem::getEmployee).toList()
                : employeeField.getValue() != null ? List.of(employeeField.getValue()) : List.of();
        if (report == null || employees.isEmpty()) {
            notifications.create(messageBundle.getMessage("nothingToGrant.text"))
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            GrantResult result = accessService.grant(report, employees,
                    toSubdivision ? ReportAccessGrantSource.SUBDIVISION : ReportAccessGrantSource.DIRECT,
                    validToField.getValue());
            notifications.create(messageBundle.formatMessage("granted.text", result.created().size(), result.skipped()))
                    .withType(result.created().isEmpty() ? Notifications.Type.WARNING : Notifications.Type.SUCCESS)
                    .show();
            close(StandardOutcome.SAVE);
        } catch (ReportAccessException | AccessDeniedException e) {
            notifications.create(e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        closeWithDefaultAction();
    }
}
