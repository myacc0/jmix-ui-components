package uz.kapitalbank.umida.service.dq;

import io.jmix.core.event.AttributeChanges;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.dq.DqRule;

import java.util.Arrays;

/**
 * Applies the rule bookkeeping on every save path (the editor, services, tests), right before the
 * rule is written, so its required code and timestamp are set before validation on persist.
 */
@Component
public class DqRuleEventListener {

    private final DqRuleService ruleService;
    private final DqIssueService issueService;
    private final DqCheckService checkService;

    public DqRuleEventListener(DqRuleService ruleService, DqIssueService issueService,
                               DqCheckService checkService) {
        this.ruleService = ruleService;
        this.issueService = issueService;
        this.checkService = checkService;
    }

    @EventListener
    public void onDqRuleSaving(final EntitySavingEvent<DqRule> event) {
        ruleService.prepareForSave(event.getEntity(), event.isNewEntity());
    }

    /**
     * In the transaction that saves the rule: a rule moved to other data closes its open issues, as
     * they describe data the rule no longer checks; a reassigned rule moves the issues still open to
     * its new assignee. The closing goes first, so the issues it closes keep the assignee they had.
     * A deleted rule takes with it the checks it was the last rule of.
     */
    @EventListener
    public void onDqRuleChanged(final EntityChangedEvent<DqRule> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            checkService.removeChecksLeftWithoutRules(event.getEntityId());
            return;
        }
        if (event.getType() != EntityChangedEvent.Type.UPDATED) {
            return;
        }
        AttributeChanges changes = event.getChanges();
        if (Arrays.stream(DqRuleService.DATA_SOURCE_FIELDS).anyMatch(changes::isChanged)) {
            issueService.closeOnRuleDataSourceChanged(event.getEntityId());
        }
        if (changes.isChanged(DqRuleService.ASSIGNEE_FIELD)) {
            issueService.reassignOnRuleAssigneeChanged(event.getEntityId());
        }
    }
}
