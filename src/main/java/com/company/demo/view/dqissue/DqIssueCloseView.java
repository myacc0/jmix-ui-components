package com.company.demo.view.dqissue;

import com.company.demo.entity.DqIssue;
import com.company.demo.enums.DqIssueStatus;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

/** Closes an open issue with an outcome: only the closing statuses are offered. */
@Route(value = "dq-issues/:id/close", layout = MainView.class)
@ViewController(id = "demo_DqIssue.close")
@ViewDescriptor(path = "dq-issue-close-view.xml")
@EditedEntityContainer("dqIssueDc")
@DialogMode(width = "32em")
public class DqIssueCloseView extends StandardDetailView<DqIssue> {

    @ViewComponent
    private JmixSelect<DqIssueStatus> statusField;

    @Autowired
    private MessageBundle messageBundle;

    @Subscribe
    public void onInit(final InitEvent event) {
        statusField.setItems(DqIssueStatus.RESOLVED, DqIssueStatus.WONTFIX, DqIssueStatus.FALSE_POSITIVE);
    }

    /**
     * The issue arrives here still open, and the field shows that value as blank because it is not
     * among the offered ones. Saving it back unchanged would close nothing, so it is rejected.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (getEditedEntity().getStatus() == DqIssueStatus.OPEN) {
            ValidationErrors errors = new ValidationErrors();
            errors.add(statusField, messageBundle.getMessage("dqIssueCloseView.statusRequired"));
            event.addErrors(errors);
        }
    }
}
