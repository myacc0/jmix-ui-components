package com.company.demo.service;

import com.company.demo.entity.dq.DqIssue;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Issue bookkeeping shared by the flows that edit an issue from the issue list. */
@Service
public class DqIssueService {

    /**
     * Records that the issue was just modified. Called before the change is written, so the stamp
     * travels with the same save.
     */
    public void markUpdated(DqIssue issue) {
        issue.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Records that the issue was just closed: the moment it was resolved, and the change with it.
     * Called before the change is written, so both stamps travel with the same save.
     */
    public void markClosed(DqIssue issue) {
        LocalDateTime now = LocalDateTime.now();
        issue.setResolvedAt(now);
        issue.setUpdatedAt(now);
    }
}
