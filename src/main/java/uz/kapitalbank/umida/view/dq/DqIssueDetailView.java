package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.DescriptionList;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.model.InstanceLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dq-issues/:id", layout = MainView.class)
@ViewController(id = "umida_DqIssue.detail")
@ViewDescriptor(path = "dq-issue-detail-view.xml")
@EditedEntityContainer("dqIssueDc")
public class DqIssueDetailView extends StandardDetailView<DqIssue> {

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private DqIssueService dqIssueService;

    @Autowired
    private DqBadges dqBadges;

    @ViewComponent
    private InstanceLoader<DqIssue> dqIssueDl;

    @ViewComponent
    private JmixButton closeIssueButton;

    @ViewComponent
    private DqViolationsFragment violationsFragment;

    @ViewComponent
    private DescriptionList.Description statusValue;

    @ViewComponent
    private DescriptionList.Description severityValue;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        refresh();
    }

    @Subscribe(id = "dqIssueDc", target = Target.DATA_CONTAINER)
    public void onDqIssueDcItemChange(final InstanceContainer.ItemChangeEvent<DqIssue> event) {
        DqIssue issue = event.getItem();
        dqBadges.show(statusValue, DqBadges.ISSUE_STATUS, issue != null ? issue.getStatus() : null);
        dqBadges.show(severityValue, DqBadges.SEVERITY,
                issue != null && issue.getRule() != null ? issue.getRule().getSeverity() : null);
    }

    @Subscribe(id = "closeIssueButton", subject = "clickListener")
    public void onCloseIssueButtonClick(final ClickEvent<JmixButton> event) {
        if (!dqIssueService.canClose(getEditedEntity())) {
            return;
        }

        dialogWindows.detail(this, DqIssue.class)
                .editEntity(getEditedEntity())
                .withViewClass(DqIssueCloseView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        dqIssueDl.load();
                        refresh();
                    }
                })
                .open();
    }

    /** Shows what the loaded issue allows: closing it, and the rows its latest failed check found. */
    private void refresh() {
        DqIssue issue = getEditedEntity();
        closeIssueButton.setEnabled(dqIssueService.canClose(issue));
        violationsFragment.setCheckResult(
                DqViolationsFragment.hasViolations(issue.getCheckResult()) ? issue.getCheckResult() : null);
    }
}
