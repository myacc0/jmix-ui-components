package uz.kapitalbank.umida.service.dq;

import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.dq.DqIssue;

/**
 * Applies the issue bookkeeping on every save path (check runs, the close dialog, tests), right
 * before the issue is written, so its required code is set before validation on persist.
 */
@Component
public class DqIssueEventListener {

    private final DqIssueService issueService;

    public DqIssueEventListener(DqIssueService issueService) {
        this.issueService = issueService;
    }

    @EventListener
    public void onDqIssueSaving(final EntitySavingEvent<DqIssue> event) {
        issueService.prepareForSave(event.getEntity(), event.isNewEntity());
    }
}
