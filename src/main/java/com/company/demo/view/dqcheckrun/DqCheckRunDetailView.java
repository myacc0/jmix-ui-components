package com.company.demo.view.dqcheckrun;

import com.company.demo.component.DqBadges;
import com.company.demo.entity.dq.DqCheckRun;
import com.company.demo.entity.dq.DqCheckRunResult;
import com.company.demo.enums.dq.DqCheckResultStatus;
import com.company.demo.service.DqSampleViolationsService;
import com.company.demo.utils.StringUtils;
import com.company.demo.view.main.MainView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
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

import java.util.List;
import java.util.Map;

@Route(value = "dq-check-runs/:id", layout = MainView.class)
@ViewController(id = "demo_DqCheckRun.detail")
@ViewDescriptor(path = "dq-check-run-detail-view.xml")
@EditedEntityContainer("dqCheckRunDc")
public class DqCheckRunDetailView extends StandardDetailView<DqCheckRun> {

    private static final String SAMPLE_VIOLATIONS_HEADER = "dqCheckRunDetailView.sampleViolationsAction";

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private Dialogs dialogs;

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private DqSampleViolationsService sampleViolationsService;

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

    /**
     * The sampled rows carry the columns of the rule's own samples query, so the grid showing them
     * is built per result. A value that is not the expected array of flat rows still has to be
     * readable, so it falls back to the raw JSON.
     */
    @Subscribe("checkResultsDataGrid.sampleViolationsAction")
    public void onSampleViolationsAction(final ActionPerformedEvent event) {
        DqCheckRunResult result = checkResultsDataGrid.getSingleSelectedItem();
        if (result == null) {
            return;
        }

        String sampleViolations = result.getSampleViolations();
        List<Map<String, Object>> rows = sampleViolationsService.parseRows(sampleViolations);

        if (rows.isEmpty()) {
            showCode(SAMPLE_VIOLATIONS_HEADER, CodeEditorMode.JSON,
                    sampleViolationsService.prettify(sampleViolations));
        } else {
            dialogs.createMessageDialog()
                    .withHeader(messageBundle.getMessage(SAMPLE_VIOLATIONS_HEADER))
                    .withContent(createSamplesGrid(rows))
                    .withWidth("60em")
                    .withResizable(true)
                    .open();
        }
    }

    private Component createSamplesGrid(List<Map<String, Object>> rows) {
        Grid<Map<String, Object>> grid = new Grid<>();
        grid.setItems(rows);
        grid.setColumnReorderingAllowed(true);
        grid.setWidthFull();
        grid.setHeight("25em");

        for (String column : sampleViolationsService.columns(rows)) {
            grid.addColumn(row -> StringUtils.asText(row.get(column)))
                    .setHeader(column)
                    .setAutoWidth(true)
                    .setResizable(true);
        }

        return grid;
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
