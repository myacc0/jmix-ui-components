package com.company.demo.view.dqissue;

import com.company.demo.entity.DqIssue;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "dq-issues", layout = MainView.class)
@ViewController(id = "demo_DqIssue.list")
@ViewDescriptor(path = "dq-issue-list-view.xml")
@LookupComponent("dqIssuesDataGrid")
@DialogMode(width = "64em")
public class DqIssueListView extends StandardListView<DqIssue> {
}