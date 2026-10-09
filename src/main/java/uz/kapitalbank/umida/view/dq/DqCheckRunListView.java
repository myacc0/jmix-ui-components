package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.service.dq.DqCheckRunService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The check runs, split in two tabs: the journal of the runs that have ended, newest first, and the
 * runs still in progress. A run moves from one tab to the other when it ends, so the tab being
 * switched to is reloaded.
 */
@Route(value = "dq-check-runs", layout = MainView.class)
@ViewController(id = "umida_DqCheckRun.list")
@ViewDescriptor(path = "dq-check-run-list-view.xml")
@LookupComponent("dqCheckRunsDataGrid")
@DialogMode(width = "64em")
public class DqCheckRunListView extends StandardListView<DqCheckRun> {

    private static final String RUNNING_TAB = "runningTab";

    @Autowired
    private ViewNavigators viewNavigators;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqCheckRunService checkRunService;

    @ViewComponent
    private CollectionLoader<DqCheckRun> dqCheckRunsDl;

    @ViewComponent
    private CollectionLoader<DqCheckRun> runningCheckRunsDl;

    /** "Triggered by" captions of the loaded runs of both tabs, by run id. */
    private final Map<UUID, String> triggeredByCaptions = new HashMap<>();

    @Subscribe(id = "dqCheckRunsDl", target = Target.DATA_LOADER)
    public void onDqCheckRunsDlPostLoad(final CollectionLoader.PostLoadEvent<DqCheckRun> event) {
        triggeredByCaptions.putAll(checkRunService.getTriggeredByCaptions(event.getLoadedEntities()));
    }

    @Subscribe(id = "runningCheckRunsDl", target = Target.DATA_LOADER)
    public void onRunningCheckRunsDlPostLoad(final CollectionLoader.PostLoadEvent<DqCheckRun> event) {
        triggeredByCaptions.putAll(checkRunService.getTriggeredByCaptions(event.getLoadedEntities()));
    }

    @Subscribe("checkRunsTabSheet")
    public void onCheckRunsTabSheetSelectedChange(final JmixTabSheet.SelectedChangeEvent event) {
        if (event.isInitialSelection()) {
            return;
        }
        boolean running = event.getSelectedTab() != null
                && event.getSelectedTab().getId().filter(RUNNING_TAB::equals).isPresent();
        if (running) {
            runningCheckRunsDl.load();
        } else {
            dqCheckRunsDl.load();
        }
    }

    @Supply(to = "dqCheckRunsDataGrid.status", subject = "renderer")
    private Renderer<DqCheckRun> dqCheckRunsDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.RUN_STATUS, DqCheckRun::getStatus);
    }

    @Supply(to = "runningCheckRunsDataGrid.status", subject = "renderer")
    private Renderer<DqCheckRun> runningCheckRunsDataGridStatusRenderer() {
        return dqBadges.renderer(DqBadges.RUN_STATUS, DqCheckRun::getStatus);
    }

    @Supply(to = "dqCheckRunsDataGrid.triggeredBy", subject = "renderer")
    private Renderer<DqCheckRun> dqCheckRunsDataGridTriggeredByRenderer() {
        return triggeredByRenderer();
    }

    @Supply(to = "runningCheckRunsDataGrid.triggeredBy", subject = "renderer")
    private Renderer<DqCheckRun> runningCheckRunsDataGridTriggeredByRenderer() {
        return triggeredByRenderer();
    }

    private Renderer<DqCheckRun> triggeredByRenderer() {
        return new TextRenderer<>(run -> triggeredByCaptions.getOrDefault(run.getId(), ""));
    }

    @Subscribe(id = "runCheckButton", subject = "clickListener")
    public void onRunCheckButtonClick(final ClickEvent<JmixButton> event) {
        // registers this view as the return target, so closing the new-run page comes back here
        // instead of falling back to the parent layout
        viewNavigators.view(this, DqCheckRunNewView.class)
                .withBackwardNavigation(true)
                .navigate();
    }
}
