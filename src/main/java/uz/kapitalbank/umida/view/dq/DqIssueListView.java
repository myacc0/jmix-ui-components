package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dq-issues", layout = MainView.class)
@ViewController(id = "umida_DqIssue.list")
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

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqIssueService dqIssueService;

    @Supply(to = "dqIssuesDataGrid.status", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_STATUS, DqIssue::getStatus);
    }

    @Supply(to = "dqIssuesDataGrid.severity", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, dqIssue -> dqIssue.getRule().getSeverity());
    }

    /** Only an open issue can be closed, and only by a user of the subdivision its rule is assigned to. */
    @Install(to = "dqIssuesDataGrid.closeIssueAction", subject = "enabledRule")
    private boolean closeIssueActionEnabledRule() {
        return dqIssueService.canClose(dqIssuesDataGrid.getSingleSelectedItem());
    }

    @Subscribe("dqIssuesDataGrid.closeIssueAction")
    public void onDqIssuesDataGridCloseIssue(final ActionPerformedEvent event) {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        if (!dqIssueService.canClose(issue)) {
            return;
        }

        dialogWindows.detail(this, DqIssue.class)
                .editEntity(issue)
                .withViewClass(DqIssueCloseView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        dqIssuesDl.load();
                    }
                })
                .open();
    }

    @Install(to = "dqIssuesDataGrid.violationsAction", subject = "enabledRule")
    private boolean violationsActionEnabledRule() {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        return issue != null && DqViolationsFragment.hasViolations(issue.getCheckResult());
    }

    @Subscribe("dqIssuesDataGrid.violationsAction")
    public void onDqIssuesDataGridViolations(final ActionPerformedEvent event) {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        if (issue == null || issue.getCheckResult() == null) {
            return;
        }
        dialogWindows.view(this, DqCheckRunViolationsView.class)
                .withViewConfigurer(view -> view.setCheckResult(issue.getCheckResult()))
                .open();
    }
}
