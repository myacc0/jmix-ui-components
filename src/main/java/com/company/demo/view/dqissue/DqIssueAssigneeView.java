package com.company.demo.view.dqissue;

import com.company.demo.entity.DqIssue;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-issues/:id/assignee", layout = MainView.class)
@ViewController(id = "demo_DqIssue.assignee")
@ViewDescriptor(path = "dq-issue-assignee-view.xml")
@EditedEntityContainer("dqIssueDc")
@DialogMode(width = "32em")
public class DqIssueAssigneeView extends StandardDetailView<DqIssue> {
}
