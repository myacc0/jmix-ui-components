package com.company.demo.view.dqissue;

import com.company.demo.entity.DqIssue;
import com.company.demo.enums.DqIssueStatus;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dq-issues", layout = MainView.class)
@ViewController(id = "demo_DqIssue.list")
@ViewDescriptor(path = "dq-issue-list-view.xml")
@LookupComponent("dqIssuesDataGrid")
@DialogMode(width = "64em")
public class DqIssueListView extends StandardListView<DqIssue> {

    @Autowired
    private DialogWindows dialogWindows;

    @ViewComponent
    private DataGrid<DqIssue> dqIssuesDataGrid;

    @ViewComponent
    private CollectionLoader<DqIssue> dqIssuesDl;

    /** Only an open issue can be assigned: once it is resolved or closed, the assignee is history. */
    @Install(to = "dqIssuesDataGrid.setAssigneeAction", subject = "enabledRule")
    private boolean setAssigneeActionEnabledRule() {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        return issue != null && issue.getStatus() == DqIssueStatus.OPEN;
    }

    @Subscribe("dqIssuesDataGrid.setAssigneeAction")
    public void onDqIssuesDataGridSetAssignee(final ActionPerformedEvent event) {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        if (issue == null || issue.getStatus() != DqIssueStatus.OPEN) {
            return;
        }

        dialogWindows.detail(this, DqIssue.class)
                .editEntity(issue)
                .withViewClass(DqIssueAssigneeView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        dqIssuesDl.load();
                    }
                })
                .open();
    }
}
