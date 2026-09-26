package com.company.demo.view.dq;

import com.company.demo.entity.dq.DqIssue;
import com.company.demo.service.dq.DqIssueService;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
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
