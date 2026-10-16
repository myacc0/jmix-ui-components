package uz.kapitalbank.umida.service.dq;

import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.dq.DqCheck;

/**
 * Applies the check bookkeeping on every save path (the editor, services, tests), right before the
 * check is written, so its required code is set before validation on persist.
 */
@Component
public class DqCheckEventListener {

    private final DqCheckService checkService;

    public DqCheckEventListener(DqCheckService checkService) {
        this.checkService = checkService;
    }

    @EventListener
    public void onDqCheckSaving(final EntitySavingEvent<DqCheck> event) {
        checkService.prepareForSave(event.getEntity(), event.isNewEntity());
    }
}
