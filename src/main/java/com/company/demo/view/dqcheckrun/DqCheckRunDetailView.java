package com.company.demo.view.dqcheckrun;

import com.company.demo.component.DqBadges;
import com.company.demo.entity.DqCheckRun;
import com.company.demo.entity.DqCheckRunResult;
import com.company.demo.enums.DqCheckResultStatus;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;
import io.jmix.flowui.view.*;
import com.vaadin.flow.data.renderer.Renderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

@Route(value = "dq-check-runs/:id", layout = MainView.class)
@ViewController(id = "demo_DqCheckRun.detail")
@ViewDescriptor(path = "dq-check-run-detail-view.xml")
@EditedEntityContainer("dqCheckRunDc")
public class DqCheckRunDetailView extends StandardDetailView<DqCheckRun> {

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private Dialogs dialogs;

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

    /** Violating rows are only sampled for a rule that failed: any other status has nothing to show. */
    @Install(to = "checkResultsDataGrid.sampleViolationsAction", subject = "enabledRule")
    private boolean sampleViolationsActionEnabledRule() {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        return result != null && result.getStatus() == DqCheckResultStatus.FAILED;
    }

    @Subscribe("checkResultsDataGrid.executedQueryAction")
    public void onExecutedQueryAction(final ActionPerformedEvent event) {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        if (result != null) {
            showCode("dqCheckRunDetailView.executedQueryAction", CodeEditorMode.SQL, result.getExecutedQuery());
        }
    }

    @Subscribe("checkResultsDataGrid.sampleViolationsAction")
    public void onSampleViolationsAction(final ActionPerformedEvent event) {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        if (result != null) {
            showCode("dqCheckRunDetailView.sampleViolationsAction", CodeEditorMode.JSON, result.getSampleViolations());
        }
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
