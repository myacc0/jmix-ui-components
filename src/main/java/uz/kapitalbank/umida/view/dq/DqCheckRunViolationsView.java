package uz.kapitalbank.umida.view.dq;

import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;

/**
 * The rows that violated the rule of a check result, as a dialog over {@link DqViolationsFragment}.
 * Call {@link #setCheckResult(DqCheckRunResult)} before opening.
 */
@ViewController(id = "umida_DqCheckRunViolationsView")
@ViewDescriptor(path = "dq-check-run-violations-view.xml")
@DialogMode(width = "80em", height = "44em", resizable = true)
public class DqCheckRunViolationsView extends StandardView {

    @ViewComponent
    private DqViolationsFragment violationsFragment;

    private DqCheckRunResult checkResult;

    public void setCheckResult(DqCheckRunResult checkResult) {
        this.checkResult = checkResult;
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        violationsFragment.setCheckResult(checkResult);
    }
}
