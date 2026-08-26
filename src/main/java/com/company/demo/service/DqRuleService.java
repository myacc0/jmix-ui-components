package com.company.demo.service;

import com.company.demo.entity.dq.DqRule;
import com.company.demo.entity.User;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/** Rule bookkeeping that the editor delegates rather than asking the user to fill in. */
@Service
public class DqRuleService {

    private final CurrentAuthentication currentAuthentication;

    public DqRuleService(CurrentAuthentication currentAuthentication) {
        this.currentAuthentication = currentAuthentication;
    }

    /**
     * Gives an unowned rule to the user of the current session. A rule that already has an owner
     * keeps it: editing someone else's rule does not transfer it.
     */
    public void assignOwner(DqRule rule) {
        if (rule.getOwner() != null) {
            return;
        }

        UserDetails user = currentAuthentication.getUser();
        if (user instanceof User owner) {
            rule.setOwner(owner);
        }
    }
}
