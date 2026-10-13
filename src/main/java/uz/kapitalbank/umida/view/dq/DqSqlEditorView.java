package uz.kapitalbank.umida.view.dq;

import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.lang.Nullable;

import java.util.function.Consumer;

/**
 * A large SQL editor opened as a dialog over a form whose own editor is too small to work in, the
 * way the report editor expands its data set scripts. The edited text goes back to the form only
 * when the user applies it.
 */
@ViewController(id = "umida_DqSqlEditorView")
@ViewDescriptor(path = "dq-sql-editor-view.xml")
@DialogMode(width = "90vw", height = "85vh", resizable = true)
public class DqSqlEditorView extends StandardView {

    @ViewComponent
    private CodeEditor editor;

    private Consumer<String> applyHandler = value -> {
    };

    public void setValue(@Nullable String value) {
        editor.setValue(value != null ? value : "");
    }

    /** Receives the edited text when the user applies it; cancelling leaves the form as it was. */
    public void setApplyHandler(Consumer<String> applyHandler) {
        this.applyHandler = applyHandler;
    }

    @Subscribe("applyAction")
    public void onApplyAction(final ActionPerformedEvent event) {
        applyHandler.accept(editor.getValue());
        close(StandardOutcome.SAVE);
    }
}
