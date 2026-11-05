package uz.kapitalbank.umida.view.report.access;

import io.jmix.core.security.SecurityContextHelper;
import io.jmix.flowui.UiEventPublisher;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.service.report.ReportAccessChangedEvent;

/**
 * Hands a change of the access to the reports over to the open screens of every user: the menu item and the
 * screen of the access management count the pending requests again, see
 * {@link uz.kapitalbank.umida.view.main.MainView} and {@link ReportAccessManagementView}. The service knows
 * nothing of the screens, so the bridge lives here.
 *
 * <p>The event reaches a screen under no reliable authentication: the publisher runs it as the recipient by
 * {@code SystemAuthenticator.runWithUser}, possibly after that is over, and that authentication carries only
 * the roles assigned in the database. The roles an LDAP user gets at the login ({@code jmix.ldap.default-roles},
 * the viewer roles of the reports among them) are not there, so a data loader run under it reads nothing.
 * A screen handles the event with {@link #runAs} and the authentication its user logged in with.
 */
@Component
public class ReportAccessUiEventBridge {

    private final UiEventPublisher uiEventPublisher;

    public ReportAccessUiEventBridge(UiEventPublisher uiEventPublisher) {
        this.uiEventPublisher = uiEventPublisher;
    }

    @EventListener
    public void onReportAccessChanged(ReportAccessChangedEvent event) {
        // every user: who decides on a request is known only to the service
        uiEventPublisher.publishEventForUsers(new RequestsChangedUiEvent(this), null);
    }

    /**
     * Runs the action under the given authentication — that of the user of the screen, taken while the screen
     * was opened — and restores the previous one.
     */
    public static void runAs(Authentication authentication, Runnable action) {
        Authentication previous = SecurityContextHelper.getAuthentication();
        SecurityContextHelper.setAuthentication(authentication);
        try {
            action.run();
        } finally {
            SecurityContextHelper.setAuthentication(previous);
        }
    }

    /** The pending requests may have changed; received by the views with an {@code @EventListener}. */
    public static class RequestsChangedUiEvent extends ApplicationEvent {

        public RequestsChangedUiEvent(Object source) {
            super(source);
        }
    }
}
