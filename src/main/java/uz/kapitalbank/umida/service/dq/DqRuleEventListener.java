package uz.kapitalbank.umida.service.dq;

import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.dq.DqRule;

/**
 * Applies the rule bookkeeping on every save path (the editor, services, tests), right before the
 * rule is written, so its required code and timestamp are set before validation on persist.
 */
@Component
public class DqRuleEventListener {

    private final DqRuleService ruleService;

    public DqRuleEventListener(DqRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @EventListener
    public void onDqRuleSaving(final EntitySavingEvent<DqRule> event) {
        ruleService.prepareForSave(event.getEntity(), event.isNewEntity());
    }
}
