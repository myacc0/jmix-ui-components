package com.company.demo.service;

import com.company.demo.entity.DqIssue;
import com.company.demo.enums.DqIssueStatus;
import io.jmix.core.event.EntitySavingEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Stamps the moment an issue stops being open. Closing happens through the close dialog, but the
 * rule lives here so that it holds on every path that saves a {@link DqIssue} — a service, an
 * import, or a plain {@code DataManager.save()}.
 */
@Component
public class DqIssueEntityListener {

    /**
     * An issue that carries a closing status without a resolution time has just been closed: both
     * timestamps are set. Once stamped it is left alone, so re-saving a closed issue — editing its
     * notes, for instance — does not move the resolution time.
     */
    @EventListener
    public void onDqIssueSaving(final EntitySavingEvent<DqIssue> event) {
        DqIssue issue = event.getEntity();
        DqIssueStatus status = issue.getStatus();

        if (status == null || status == DqIssueStatus.OPEN || issue.getResolvedAt() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        issue.setResolvedAt(now);
        issue.setUpdatedAt(now);
    }
}
