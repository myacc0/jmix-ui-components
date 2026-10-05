package uz.kapitalbank.umida.security;

import io.jmix.core.security.event.UserDisabledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends the HTTP sessions of a user the moment the user is deactivated — by an administrator or by
 * {@code DismissedEmployeeUsersJob} — so that a dismissed employee already logged in loses access with the next
 * request instead of keeping it until the session times out.
 * <p>
 * Jmix publishes {@link UserDisabledEvent} when {@code User.active} goes from {@code true} to {@code false}, but
 * nothing in the framework acts on it. The sessions are expired in the registry, and the concurrent session
 * filter Jmix installs logs them out on their next request. Runs after the commit, so a rolled back
 * deactivation leaves the sessions alone.
 */
@Component("umida_DisabledUserSessionTerminator")
public class DisabledUserSessionTerminator {

    private static final Logger log = LoggerFactory.getLogger(DisabledUserSessionTerminator.class);

    private final SessionRegistry sessionRegistry;

    public DisabledUserSessionTerminator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onUserDisabled(UserDisabledEvent event) {
        String username = event.getUsername();
        int expired = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails userDetails && username.equals(userDetails.getUsername())) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                    expired++;
                }
            }
        }
        if (expired > 0) {
            log.info("Expired {} session(s) of the deactivated user {}", expired, username);
        }
    }
}
