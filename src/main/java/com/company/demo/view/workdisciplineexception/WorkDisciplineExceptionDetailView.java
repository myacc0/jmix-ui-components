package com.company.demo.view.workdisciplineexception;

import com.company.demo.entity.WorkDisciplineException;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.enums.WorkDisciplineTargetType;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.view.*;
import org.springframework.lang.Nullable;

@Route(value = "work-discipline-exceptions/:id", layout = MainView.class)
@ViewController(id = "demo_WorkDisciplineException.detail")
@ViewDescriptor(path = "work-discipline-exception-detail-view.xml")
@EditedEntityContainer("workDisciplineExceptionDc")
@DialogMode(width = "50em")
public class WorkDisciplineExceptionDetailView extends StandardDetailView<WorkDisciplineException> {

    @ViewComponent
    private JmixRadioButtonGroup<WorkDisciplineTargetType> targetTypeField;
    @ViewComponent
    private EntityPicker<Department> subdivisionField;
    @ViewComponent
    private EntityPicker<JobTitle> jobTitleField;
    @ViewComponent
    private EntityPicker<Employee> employeeField;

    @ViewComponent
    private MessageBundle messageBundle;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        targetTypeField.setValue(resolveTargetType());
        updateTargetFields();
    }

    /**
     * The target is enforced here rather than through {@code required="true"} on the components:
     * the built-in required check paints a just-revealed empty picker red before the user has had
     * any chance to fill it.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        WorkDisciplineTargetType targetType = targetTypeField.getValue();
        if (targetType == null) {
            event.getErrors().add(messageBundle.getMessage("targetTypeRequired"));
            return;
        }
        if (resolveTargetType() != targetType) {
            event.getErrors().add(messageBundle.getMessage("targetEntityRequired"));
        }
    }

    @Subscribe("targetTypeField")
    public void onTargetTypeFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<JmixRadioButtonGroup<WorkDisciplineTargetType>,
                    WorkDisciplineTargetType> event) {
        if (event.isFromClient()) {
            clearUnselectedTargets(event.getValue());
        }
        updateTargetFields();
    }

    /**
     * Restores the radio button selection of an existing record from the reference that is actually set.
     */
    @Nullable
    private WorkDisciplineTargetType resolveTargetType() {
        WorkDisciplineException exception = getEditedEntity();
        if (exception.getSubdivision() != null) {
            return WorkDisciplineTargetType.SUBDIVISION;
        }
        if (exception.getJobTitle() != null) {
            return WorkDisciplineTargetType.JOB_TITLE;
        }
        if (exception.getEmployee() != null) {
            return WorkDisciplineTargetType.EMPLOYEE;
        }
        return null;
    }

    /**
     * Keeps a single reference filled: switching the target type drops the value of the previous one.
     */
    private void clearUnselectedTargets(@Nullable WorkDisciplineTargetType targetType) {
        WorkDisciplineException exception = getEditedEntity();
        if (targetType != WorkDisciplineTargetType.SUBDIVISION) {
            exception.setSubdivision(null);
        }
        if (targetType != WorkDisciplineTargetType.JOB_TITLE) {
            exception.setJobTitle(null);
        }
        if (targetType != WorkDisciplineTargetType.EMPLOYEE) {
            exception.setEmployee(null);
        }
    }

    private void updateTargetFields() {
        WorkDisciplineTargetType targetType = targetTypeField.getValue();
        setTargetFieldActive(subdivisionField, targetType == WorkDisciplineTargetType.SUBDIVISION);
        setTargetFieldActive(jobTitleField, targetType == WorkDisciplineTargetType.JOB_TITLE);
        setTargetFieldActive(employeeField, targetType == WorkDisciplineTargetType.EMPLOYEE);
    }

    /**
     * Only visibility is toggled here: any required flag on the picker (setRequired or even
     * setRequiredIndicatorVisible) makes Jmix validate it the moment it is revealed, so a picker
     * the user has not reached yet would already be red. onValidation enforces it on save instead.
     */
    private void setTargetFieldActive(EntityPicker<?> field, boolean active) {
        field.setVisible(active);
    }
}
