package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.enums.dq.DqIssueStatus;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Closes an open issue by hand. Only the outcomes a user may choose are offered: an issue is resolved
 * by the check runs, once its data is fixed.
 */
@Route(value = "dq-issues/:id/close", layout = MainView.class)
@ViewController(id = "umida_DqIssue.close")
@ViewDescriptor(path = "dq-issue-close-view.xml")
@EditedEntityContainer("dqIssueDc")
@DialogMode(width = "32em")
public class DqIssueCloseView extends StandardDetailView<DqIssue> {

    @ViewComponent
    private JmixSelect<DqIssueStatus> statusField;

    @Autowired
    private MessageBundle messageBundle;

    @Autowired
    private DqIssueService dqIssueService;

    /** The save that carries the outcome is the moment the issue is resolved. */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        dqIssueService.markClosed(getEditedEntity());
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        statusField.setItems(DqIssueService.MANUAL_CLOSING_STATUSES);
    }

    /**
     * The issue arrives here still open, and the field shows that value as blank because it is not
     * among the offered ones. Saving it back unchanged would close nothing, so it is rejected. The
     * right to close is checked again: the list only hides the action from the others.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        if (!DqIssueService.MANUAL_CLOSING_STATUSES.contains(getEditedEntity().getStatus())) {
            errors.add(statusField, messageBundle.getMessage("dqIssueCloseView.statusRequired"));
        }
        if (!dqIssueService.isInAssigneeSubdivision(getEditedEntity())) {
            errors.add(messageBundle.getMessage("dqIssueCloseView.notAssignee"));
        }
        event.addErrors(errors);
    }
}
