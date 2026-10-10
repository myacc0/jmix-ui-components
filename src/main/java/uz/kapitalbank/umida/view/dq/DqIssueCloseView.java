package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.enums.dq.DqIssueClosingReason;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Closes an open issue by hand. Only the reasons a user may choose are offered: an issue is closed as
 * fixed by the check runs, once its data is fixed.
 */
@Route(value = "dq-issues/:id/close", layout = MainView.class)
@ViewController(id = "umida_DqIssue.close")
@ViewDescriptor(path = "dq-issue-close-view.xml")
@EditedEntityContainer("dqIssueDc")
@DialogMode(width = "32em")
public class DqIssueCloseView extends StandardDetailView<DqIssue> {

    @ViewComponent
    private JmixSelect<DqIssueClosingReason> closingReasonField;

    @Autowired
    private MessageBundle messageBundle;

    @Autowired
    private DqIssueService dqIssueService;

    /** The save that carries the reason is the moment the issue is closed. */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        dqIssueService.markClosed(getEditedEntity());
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        closingReasonField.setItems(DqIssueService.MANUAL_CLOSING_REASONS);
    }

    /**
     * Only a reason a user may choose closes the issue. The right to close is checked again: the list
     * only hides the action from the others.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        if (!DqIssueService.MANUAL_CLOSING_REASONS.contains(getEditedEntity().getClosingReason())) {
            errors.add(closingReasonField, messageBundle.getMessage("dqIssueCloseView.closingReasonRequired"));
        }
        if (!dqIssueService.isInAssigneeSubdivision(getEditedEntity())) {
            errors.add(messageBundle.getMessage("dqIssueCloseView.notAssignee"));
        }
        event.addErrors(errors);
    }
}
