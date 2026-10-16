package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.enums.dq.DqRuleType;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;


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

    @ViewComponent
    private CollectionContainer<DqIssue> dqIssuesDc;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqIssueService dqIssueService;

    @Supply(to = "dqIssuesDataGrid.status", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_STATUS, DqIssue::getStatus);
    }

    @Supply(to = "dqIssuesDataGrid.closingReason", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridClosingReasonRenderer() {
        return dqBadges.renderer(DqBadges.ISSUE_CLOSING_REASON, DqIssue::getClosingReason);
    }

    @Supply(to = "dqIssuesDataGrid.severity", subject = "renderer")
    private Renderer<DqIssue> dqIssuesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, dqIssue -> dqIssue.getRule().getSeverity());
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        updateCloseIssueActionVisibility();
    }

    /** Only an open issue can be closed, and only by a user of the subdivision its rule is assigned to. */
    @Install(to = "dqIssuesDataGrid.closeIssueAction", subject = "enabledRule")
    private boolean closeIssueActionEnabledRule() {
        return dqIssueService.canClose(selectedIssue());
    }

    @Subscribe(id = "dqIssuesDc", target = Target.DATA_CONTAINER)
    public void onDqIssuesDcItemChange(final InstanceContainer.ItemChangeEvent<DqIssue> event) {
        updateCloseIssueActionVisibility();
    }

    /** A reload keeps the selection but brings a fresh instance of the selected issue, maybe closed meanwhile. */
    @Subscribe(id = "dqIssuesDc", target = Target.DATA_CONTAINER)
    public void onDqIssuesDcCollectionChange(final CollectionContainer.CollectionChangeEvent<DqIssue> event) {
        updateCloseIssueActionVisibility();
    }

    /** The close button is not shown at all for an issue the current user cannot close. */
    private void updateCloseIssueActionVisibility() {
        dqIssuesDataGrid.getAction("closeIssueAction").setVisible(dqIssueService.canClose(selectedIssue()));
    }

    /** The loaded instance of the selected issue: after a reload the grid may still hold the previous one. */
    @Nullable
    private DqIssue selectedIssue() {
        DqIssue selected = dqIssuesDataGrid.getSingleSelectedItem();
        return selected != null ? dqIssuesDc.getItemOrNull(selected.getId()) : null;
    }

    @Subscribe("dqIssuesDataGrid.closeIssueAction")
    public void onDqIssuesDataGridCloseIssue(final ActionPerformedEvent event) {
        DqIssue issue = selectedIssue();
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

    /**
     * The rows the latest failed check found — or, for a cross-source rule, which finds no rows,
     * the values the check compared.
     */
    @Install(to = "dqIssuesDataGrid.violationsAction", subject = "enabledRule")
    private boolean violationsActionEnabledRule() {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        if (issue == null) {
            return false;
        }
        return isCrossSource(issue)
                ? DqReconciliationFragment.hasReconciliation(issue.getCheckResult())
                : DqViolationsFragment.hasViolations(issue.getCheckResult());
    }

    @Subscribe("dqIssuesDataGrid.violationsAction")
    public void onDqIssuesDataGridViolations(final ActionPerformedEvent event) {
        DqIssue issue = dqIssuesDataGrid.getSingleSelectedItem();
        if (issue == null || issue.getCheckResult() == null) {
            return;
        }
        if (isCrossSource(issue)) {
            dialogWindows.view(this, DqCheckRunReconciliationView.class)
                    .withViewConfigurer(view -> view.setCheckResult(issue.getCheckResult()))
                    .open();
        } else {
            dialogWindows.view(this, DqCheckRunViolationsView.class)
                    .withViewConfigurer(view -> view.setCheckResult(issue.getCheckResult()))
                    .open();
        }
    }

    private static boolean isCrossSource(DqIssue issue) {
        return issue.getRule() != null && issue.getRule().getRuleType() == DqRuleType.CROSS_SOURCE_AGGREGATED;
    }
}
