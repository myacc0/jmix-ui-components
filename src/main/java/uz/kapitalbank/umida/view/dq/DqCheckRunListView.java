package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.service.dq.DqCheckExecutorService;
import uz.kapitalbank.umida.service.dq.DqCheckRunService;
import uz.kapitalbank.umida.service.dq.DqCheckService;
import uz.kapitalbank.umida.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.tabsheet.JmixTabSheet;
import io.jmix.flowui.facet.UrlQueryParametersFacet;
import io.jmix.flowui.facet.urlqueryparameters.AbstractUrlQueryParametersBinder;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The checks and their runs, in three tabs: the catalog of checks, where runs are started, the runs
 * still in progress, and the journal of the runs that have ended, newest first. A run moves from one
 * tab to the other when it ends, so the tab being switched to is reloaded. A check with no active
 * rule has nothing to run: it is shown dimmed and cannot be started.
 * <p>
 * The selected tab is kept in the URL, so that closing a check or a run opened from a tab comes back
 * to that tab.
 */
@Route(value = "dq-check-runs", layout = MainView.class)
@ViewController(id = "umida_DqCheckRun.list")
@ViewDescriptor(path = "dq-check-run-list-view.xml")
@LookupComponent("dqCheckRunsDataGrid")
@DialogMode(width = "64em")
public class DqCheckRunListView extends StandardListView<DqCheckRun> {

    private static final String RUNNING_TAB = "runningTab";
    private static final String CATALOG_TAB = "catalogTab";

    @Autowired
    private Notifications notifications;

    @Autowired
    private DqCheckExecutorService checkExecutorService;

    @Autowired
    private DqCheckService checkService;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private DqCheckRunService checkRunService;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @ViewComponent
    private CollectionLoader<DqCheckRun> dqCheckRunsDl;

    @ViewComponent
    private CollectionLoader<DqCheckRun> runningCheckRunsDl;

    @ViewComponent
    private CollectionLoader<DqCheck> dqChecksDl;

    @ViewComponent
    private DataGrid<DqCheck> dqChecksDataGrid;

    @ViewComponent
    private JmixTabSheet checkRunsTabSheet;

    @ViewComponent
    private MessageBundle messageBundle;

    /** "Triggered by" captions of the loaded runs of both tabs, by run id. */
    private final Map<UUID, String> triggeredByCaptions = new HashMap<>();

    /** Rule counts of the loaded checks, by check id. */
    private final Map<UUID, DqCheckService.RuleCounts> ruleCounts = new HashMap<>();

    /**
     * The "mine" filter of the catalog: the checks created by the current user. It carries no value
     * while the checkbox is clear, and the loader then skips it.
     */
    private final PropertyCondition mineCondition = PropertyCondition.equal("createdBy", null).skipNullOrEmpty();

    @Subscribe(id = "dqCheckRunsDl", target = Target.DATA_LOADER)
    public void onDqCheckRunsDlPostLoad(final CollectionLoader.PostLoadEvent<DqCheckRun> event) {
        triggeredByCaptions.putAll(checkRunService.getTriggeredByCaptions(event.getLoadedEntities()));
    }

    @Subscribe(id = "runningCheckRunsDl", target = Target.DATA_LOADER)
    public void onRunningCheckRunsDlPostLoad(final CollectionLoader.PostLoadEvent<DqCheckRun> event) {
        triggeredByCaptions.putAll(checkRunService.getTriggeredByCaptions(event.getLoadedEntities()));
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        ViewControllerUtils.getViewFacet(this, UrlQueryParametersFacet.class)
                .registerBinder(new SelectedTabBinder());
        addMineCondition();
    }

    /** Adds the "mine" filter next to the conditions of the column header filters, which share the root condition. */
    private void addMineCondition() {
        Condition root = dqChecksDl.getCondition();
        LogicalCondition and = root instanceof LogicalCondition logical ? logical : LogicalCondition.and();
        if (root != null && root != and) {
            and.add(root);
        }
        and.add(mineCondition);
        dqChecksDl.setCondition(and);
    }

    @Subscribe("mineField")
    public void onMineFieldValueChange(final AbstractField.ComponentValueChangeEvent<JmixCheckbox, Boolean> event) {
        mineCondition.setParameterValue(Boolean.TRUE.equals(event.getValue())
                ? currentAuthentication.getUser().getUsername()
                : null);
        dqChecksDl.load();
    }

    @Subscribe(id = "dqChecksDl", target = Target.DATA_LOADER)
    public void onDqChecksDlPostLoad(final CollectionLoader.PostLoadEvent<DqCheck> event) {
        ruleCounts.clear();
        ruleCounts.putAll(checkService.countRules(event.getLoadedEntities()));
    }

    @Subscribe("checkRunsTabSheet")
    public void onCheckRunsTabSheetSelectedChange(final JmixTabSheet.SelectedChangeEvent event) {
        if (event.isInitialSelection()) {
            return;
        }
        if (hasId(event.getSelectedTab(), RUNNING_TAB)) {
            runningCheckRunsDl.load();
        } else if (hasId(event.getSelectedTab(), CATALOG_TAB)) {
            dqChecksDl.load();
        } else {
            dqCheckRunsDl.load();
        }
    }

    private static boolean hasId(Tab tab, String tabId) {
        return tab != null && tab.getId().filter(tabId::equals).isPresent();
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

    @Supply(to = "dqChecksDataGrid.rules", subject = "renderer")
    private Renderer<DqCheck> dqChecksDataGridRulesRenderer() {
        return new TextRenderer<>(check -> {
            DqCheckService.RuleCounts counts = ruleCountsOf(check);
            return counts.active() + " / " + counts.total();
        });
    }

    /** A check with no active rule is shown dimmed: it has nothing to run. */
    @Install(to = "dqChecksDataGrid", subject = "partNameGenerator")
    private String dqChecksDataGridPartNameGenerator(final DqCheck check) {
        return isRunnable(check) ? null : "dq-inactive-row";
    }

    @Install(to = "dqChecksDataGrid.runAction", subject = "enabledRule")
    private boolean dqChecksDataGridRunActionEnabledRule() {
        DqCheck check = dqChecksDataGrid.getSingleSelectedItem();
        return check != null && isRunnable(check);
    }

    /**
     * Starts a run of the selected check and shows it among the running ones: the run is executed in
     * the background, and its outcome shows up in the journal.
     */
    @Subscribe("dqChecksDataGrid.runAction")
    public void onDqChecksDataGridRunAction(final ActionPerformedEvent event) {
        DqCheck check = dqChecksDataGrid.getSingleSelectedItem();
        if (check == null) {
            return;
        }
        DqCheckRun checkRun;
        try {
            checkRun = checkExecutorService.startCheckRun(check);
        } catch (RuntimeException e) {
            notifications.create(messageBundle.getMessage("dqCheckRunListView.runFailed"), e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            // the rules of the check may have changed meanwhile
            dqChecksDl.load();
            return;
        }

        notifications.create(messageBundle.formatMessage("dqCheckRunListView.runStarted",
                        check.getCode(), checkRun.getRulesTotal()))
                .withType(Notifications.Type.SUCCESS)
                .show();

        // selecting the tab reloads the running runs, the new one among them
        selectTab(RUNNING_TAB);
    }

    private void selectTab(String tabId) {
        for (int i = 0; i < checkRunsTabSheet.getTabCount(); i++) {
            Tab tab = checkRunsTabSheet.getTabAt(i);
            if (hasId(tab, tabId)) {
                checkRunsTabSheet.setSelectedTab(tab);
                return;
            }
        }
    }

    private boolean isRunnable(DqCheck check) {
        return ruleCountsOf(check).active() > 0;
    }

    private DqCheckService.RuleCounts ruleCountsOf(DqCheck check) {
        return ruleCounts.getOrDefault(check.getId(), DqCheckService.RuleCounts.NONE);
    }

    /** Keeps the id of the selected tab in the {@value #PARAMETER} URL query parameter. */
    private class SelectedTabBinder extends AbstractUrlQueryParametersBinder {

        private static final String PARAMETER = "tab";

        SelectedTabBinder() {
            checkRunsTabSheet.addSelectedChangeListener(event -> {
                if (event.isInitialSelection() || event.getSelectedTab() == null) {
                    return;
                }
                event.getSelectedTab().getId().ifPresent(tabId -> fireQueryParametersChanged(
                        new UrlQueryParametersFacet.UrlQueryParametersChangeEvent(this,
                                QueryParameters.of(PARAMETER, tabId))));
            });
        }

        @Override
        public void updateState(QueryParameters queryParameters) {
            queryParameters.getSingleParameter(PARAMETER).ifPresent(DqCheckRunListView.this::selectTab);
        }

        @Override
        public Component getComponent() {
            return checkRunsTabSheet;
        }
    }
}
