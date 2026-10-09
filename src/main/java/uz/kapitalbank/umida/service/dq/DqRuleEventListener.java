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

    public DqRuleEventListener(DqRuleService ruleService, DqIssueService issueService) {
        this.ruleService = ruleService;
        this.issueService = issueService;
    }

    @EventListener
    public void onDqRuleSaving(final EntitySavingEvent<DqRule> event) {
        ruleService.prepareForSave(event.getEntity(), event.isNewEntity());
    }

    /**
     * A rule moved to other data closes its open issues, in the transaction that saves the rule:
     * they describe data the rule no longer checks.
     */
    @EventListener
    public void onDqRuleChanged(final EntityChangedEvent<DqRule> event) {
        if (event.getType() != EntityChangedEvent.Type.UPDATED) {
            return;
        }
        AttributeChanges changes = event.getChanges();
        if (Arrays.stream(DqRuleService.DATA_SOURCE_FIELDS).anyMatch(changes::isChanged)) {
            issueService.closeOnRuleDataSourceChanged(event.getEntityId());
        }
    }
}
