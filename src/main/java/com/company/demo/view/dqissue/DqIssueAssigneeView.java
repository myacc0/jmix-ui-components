package com.company.demo.view.dqissue;

import com.company.demo.entity.DqIssue;
import com.company.demo.service.DqIssueService;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dq-issues/:id/assignee", layout = MainView.class)
@ViewController(id = "demo_DqIssue.assignee")
@ViewDescriptor(path = "dq-issue-assignee-view.xml")
@EditedEntityContainer("dqIssueDc")
@DialogMode(width = "32em")
public class DqIssueAssigneeView extends StandardDetailView<DqIssue> {

    @Autowired
    private DqIssueService dqIssueService;

    /** Assigning the issue changes it, so the change time moves with the same save. */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        dqIssueService.markUpdated(getEditedEntity());
    }
}
