package uz.kapitalbank.umida.view.dq;

import io.jmix.flowui.view.*;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;

/**
 * What a cross-source rule compared in a check, as a dialog over {@link DqReconciliationFragment}.
 * Call {@link #setCheckResult(DqCheckRunResult)} before opening.
 */
@ViewController(id = "umida_DqCheckRunReconciliationView")
@ViewDescriptor(path = "dq-check-run-reconciliation-view.xml")
@DialogMode(width = "70em", height = "34em", resizable = true)
public class DqCheckRunReconciliationView extends StandardView {

    @ViewComponent
    private DqReconciliationFragment reconciliationFragment;

    private DqCheckRunResult checkResult;

    public void setCheckResult(DqCheckRunResult checkResult) {
        this.checkResult = checkResult;
    }

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        reconciliationFragment.setCheckResult(checkResult);
    }
}
