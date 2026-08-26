package com.company.demo.view.dqissue;

import com.company.demo.entity.dq.DqIssue;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "dq-issues/:id", layout = MainView.class)
@ViewController(id = "demo_DqIssue.detail")
@ViewDescriptor(path = "dq-issue-detail-view.xml")
@EditedEntityContainer("dqIssueDc")
public class DqIssueDetailView extends StandardDetailView<DqIssue> {
}