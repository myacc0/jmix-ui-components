package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.dq.DqCheckRunResult;
import uz.kapitalbank.umida.enums.dq.DqCheckResultStatus;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

@Route(value = "dq-check-runs/:id", layout = MainView.class)
@ViewController(id = "umida_DqCheckRun.detail")
@ViewDescriptor(path = "dq-check-run-detail-view.xml")
@EditedEntityContainer("dqCheckRunDc")
public class DqCheckRunDetailView extends StandardDetailView<DqCheckRun> {

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private Dialogs dialogs;

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private UiComponents uiComponents;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private DataGrid<DqCheckRunResult> checkResultsDataGrid;

    @Supply(to = "checkResultsDataGrid.status", subject = "renderer")
    private Renderer<DqCheckRunResult> checkResultsDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.RESULT_STATUS, DqCheckRunResult::getStatus);
    }

    /**
     * Only a rule that failed has violating rows to show, and only a rule type that can point at
     * individual rows leaves a query to read them with.
     */
    @Install(to = "checkResultsDataGrid.violationsAction", subject = "enabledRule")
    private boolean violationsActionEnabledRule() {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        return result != null
                && result.getStatus() == DqCheckResultStatus.FAILED
                && result.getViolationsQuery() != null
                && !result.getViolationsQuery().isBlank();
    }

    @Subscribe("checkResultsDataGrid.executedQueryAction")
    public void onExecutedQueryAction(final ActionPerformedEvent event) {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        if (result != null) {
            showCode("dqCheckRunDetailView.executedQueryAction", CodeEditorMode.SQL, result.getExecutedQuery());
        }
    }

    @Subscribe("checkResultsDataGrid.violationsAction")
    public void onViolationsAction(final ActionPerformedEvent event) {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        if (result == null) {
            return;
        }
        dialogWindows.view(this, DqCheckRunViolationsView.class)
                .withViewConfigurer(view -> view.setCheckResult(result))
                .open();
    }

    /**
     * Shows the stored text of a check result in a read-only editor, or a placeholder when the run
     * recorded nothing for that attribute.
     */
    private void showCode(String headerKey, CodeEditorMode mode, @Nullable String value) {
        Dialogs.MessageDialogBuilder builder = dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage(headerKey))
                .withWidth("50em")
                .withResizable(true);

        if (value == null || value.isBlank()) {
            builder.withText(messageBundle.getMessage("dqCheckRunDetailView.noContent"));
        } else {
            builder.withContent(createCodeEditor(mode, value));
        }

        builder.open();
    }

    private CodeEditor createCodeEditor(CodeEditorMode mode, String value) {
        CodeEditor codeEditor = uiComponents.create(CodeEditor.class);
        codeEditor.setMode(mode);
        codeEditor.setValue(value);
        codeEditor.setReadOnly(true);
        codeEditor.setTextWrap(true);
        codeEditor.setShowPrintMargin(false);
        codeEditor.setWidthFull();
        codeEditor.setHeight("25em");
        return codeEditor;
    }
}
