package uz.kapitalbank.umida.view.dq;

import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.entity.dq.DqCheck;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.service.dq.DqBadges;
import uz.kapitalbank.umida.view.main.MainView;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A check and its rules. The rules are added through the rule selection dialog and removed from the
 * grid; both only change the edited check, which keeps its rules until it is saved.
 * <p>
 * The grid pages through the rules of the edited check, as they stand in the editor, by id: a rule
 * that is no longer active stays in the check and is shown dimmed — the runs of the check leave it out.
 */
@Route(value = "dq-checks/:id", layout = MainView.class)
@ViewController(id = "umida_DqCheck.detail")
@ViewDescriptor(path = "dq-check-detail-view.xml")
@EditedEntityContainer("dqCheckDc")
public class DqCheckDetailView extends StandardDetailView<DqCheck> {

    /** Matches no rule: the rules query needs at least one id, and a check may have none yet. */
    private static final List<UUID> NO_RULES = List.of(new UUID(0, 0));

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private DqBadges dqBadges;

    @Autowired
    private EntityStates entityStates;

    @ViewComponent
    private MessageBundle messageBundle;

    @ViewComponent
    private TypedTextField<String> codeField;

    @ViewComponent
    private CollectionLoader<DqRule> rulesDl;

    @ViewComponent
    private DataGrid<DqRule> rulesDataGrid;

    @Subscribe(id = "dqCheckDc", target = Target.DATA_CONTAINER)
    public void onDqCheckDcItemChange(final InstanceContainer.ItemChangeEvent<DqCheck> event) {
        // the code is assigned on the first save, so a new check has none to show yet
        codeField.setVisible(event.getItem() != null && !entityStates.isNew(event.getItem()));
        reloadRules();
    }

    /** A check without rules has nothing to run. */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (ruleIds().isEmpty()) {
            event.getErrors().add(messageBundle.getMessage("dqCheckDetailView.noRules"));
        }
    }

    @Subscribe("rulesDataGrid.addRulesAction")
    public void onRulesDataGridAddRulesAction(final ActionPerformedEvent event) {
        dialogWindows.lookup(this, DqRule.class)
                .withViewClass(DqRuleLookupView.class)
                .withViewConfigurer(lookupView -> lookupView.setExcludedRuleIds(ruleIds()))
                .withSelectHandler(this::addRules)
                .open();
    }

    @Subscribe("rulesDataGrid.removeRulesAction")
    public void onRulesDataGridRemoveRulesAction(final ActionPerformedEvent event) {
        Set<UUID> removedIds = rulesDataGrid.getSelectedItems().stream()
                .map(DqRule::getId)
                .collect(Collectors.toSet());
        if (removedIds.isEmpty()) {
            return;
        }
        Set<DqRule> rules = currentRules().stream()
                .filter(rule -> !removedIds.contains(rule.getId()))
                .collect(Collectors.toCollection(HashSet::new));
        getEditedEntity().setRules(rules);
        reloadRules();
    }

    /** An inactive rule is shown dimmed: it stays in the check, but the runs leave it out. */
    @Install(to = "rulesDataGrid", subject = "partNameGenerator")
    private String rulesDataGridPartNameGenerator(final DqRule rule) {
        return Boolean.TRUE.equals(rule.getActive()) ? null : "inactive-row";
    }

    @Supply(to = "rulesDataGrid.dimension", subject = "renderer")
    private Renderer<DqRule> rulesDataGridDimensionRenderer() {
        return dqBadges.renderer(DqBadges.DIMENSION, DqRule::getDimension);
    }

    @Supply(to = "rulesDataGrid.severity", subject = "renderer")
    private Renderer<DqRule> rulesDataGridSeverityRenderer() {
        return dqBadges.renderer(DqBadges.SEVERITY, DqRule::getSeverity);
    }

    /**
     * The selected rules join the check. They are merged into the editor first, so that the check
     * refers to the instances the editor saves it with.
     */
    private void addRules(Collection<DqRule> selected) {
        Set<DqRule> rules = new HashSet<>(currentRules());
        Set<UUID> ids = ruleIds();
        DataContext dataContext = getViewData().getDataContext();
        for (DqRule rule : selected) {
            if (!ids.contains(rule.getId())) {
                rules.add(dataContext.merge(rule));
            }
        }
        getEditedEntity().setRules(rules);
        reloadRules();
    }

    private Set<DqRule> currentRules() {
        Set<DqRule> rules = getEditedEntity().getRules();
        return rules == null ? Set.of() : rules;
    }

    private Set<UUID> ruleIds() {
        return currentRules().stream().map(DqRule::getId).collect(Collectors.toSet());
    }

    private void reloadRules() {
        Set<UUID> ids = ruleIds();
        rulesDl.setParameter("ruleIds", ids.isEmpty() ? NO_RULES : List.copyOf(ids));
        rulesDl.setFirstResult(0);
        rulesDl.load();
    }
}
