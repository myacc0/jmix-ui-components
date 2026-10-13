package uz.kapitalbank.umida.view.dq;

import uz.kapitalbank.umida.dto.dq.DqRuleValidationError;
import uz.kapitalbank.umida.entity.dq.DqIssue;
import uz.kapitalbank.umida.entity.dq.DqRule;
import uz.kapitalbank.umida.repository.DqRuleRepository;
import uz.kapitalbank.umida.service.dq.DqIssueService;
import uz.kapitalbank.umida.service.dq.DqRuleService;
import uz.kapitalbank.umida.service.dq.DqRuleValidator;
import uz.kapitalbank.umida.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.validation.ValidationErrors;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What every rule form shares, whatever the rule type: loading and saving the rule, validating its
 * configuration, and asking before a save that moves or reassigns the open issues of the rule.
 * <p>
 * A subclass descriptor declares the {@code dqRuleDc} container with the {@code dqRuleDl} loader, and
 * embeds {@link DqRuleGeneralInfoFragment} as {@code generalInfoFragment}.
 */
public abstract class AbstractDqRuleDetailView extends StandardDetailView<DqRule> {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected DqRuleRepository repository;

    @Autowired
    protected DqRuleValidator ruleValidator;

    @Autowired
    protected DqRuleService ruleService;

    @Autowired
    protected DqIssueService issueService;

    @Autowired
    protected Dialogs dialogs;

    @Autowired
    protected EntityStates entityStates;

    @ViewComponent
    protected MessageBundle messageBundle;

    @ViewComponent
    protected DqRuleGeneralInfoFragment generalInfoFragment;

    @Install(to = "dqRuleDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    protected Optional<DqRule> loadDelegate(UUID id, FetchPlan fetchPlan) {
        // Reformat the stored (compact) ruleConfig to pretty JSON for display.
        // Done before the entity enters the DataContext so it is not flagged as a change.
        return repository.findById(id, fetchPlan)
                .map(rule -> {
                    rule.setRuleConfig(JsonUtils.prettify(rule.getRuleConfig(), objectMapper));
                    return rule;
                });
    }

    /**
     * Runs on every save (create and update): the rule config must match the selected rule type.
     * Reported errors abort the save and are shown next to the offending field.
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        ValidationErrors errors = new ValidationErrors();
        for (DqRuleValidationError error : ruleValidator.validate(getEditedEntity())) {
            String field = error.field();
            Component component = DqRuleValidationError.FIELD_DUE_DAYS.equals(field)
                    ? generalInfoFragment.getDueDaysField()
                    : field != null ? resolveComponent(field) : null;
            errors.add(component, error.message());
        }
        event.addErrors(errors);
    }

    /**
     * Maps a logical config field name to the input that edits it, so the error is attached to it.
     * Returns null when no single input owns the value — the error is then shown unattached.
     */
    @Nullable
    protected abstract Component resolveComponent(String field);

    /**
     * Moving a stored rule to another data source, schema, table or column closes its open issues,
     * and assigning it to another subdivision moves them to that one (see {@link DqIssueService}),
     * so the user confirms that first, seeing the issues it changes. A rule both moved and reassigned
     * only asks about the closing: no issue is left open to move.
     */
    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        DqRule rule = getEditedEntity();
        if (entityStates.isNew(rule)) {
            return;
        }
        boolean dataSourceChanged = ruleService.isDataSourceChanged(rule);
        if (!dataSourceChanged && !ruleService.isAssigneeChanged(rule)) {
            return;
        }
        List<DqIssue> openIssues = issueService.findOpenIssues(rule);
        if (openIssues.isEmpty()) {
            return;
        }

        event.preventSave();
        String codes = openIssues.stream().map(DqIssue::getCode).collect(Collectors.joining(", "));
        String header;
        String text;
        if (dataSourceChanged) {
            header = messageBundle.getMessage("dqRuleDetailView.dataSourceChangedHeader");
            text = messageBundle.formatMessage("dqRuleDetailView.dataSourceChangedText", codes);
        } else {
            header = messageBundle.getMessage("dqRuleDetailView.assigneeChangedHeader");
            text = messageBundle.formatMessage("dqRuleDetailView.assigneeChangedText", codes,
                    rule.getAssignee() != null ? rule.getAssignee().getName() : "");
        }
        dialogs.createOptionDialog()
                .withHeader(header)
                .withText(text)
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> event.resume()),
                        new DialogAction(DialogAction.Type.NO))
                .open();
    }

    @Install(target = Target.DATA_CONTEXT)
    protected Set<Object> saveDelegate(SaveContext saveContext) {
        return Set.of(repository.save(getEditedEntity()));
    }
}
