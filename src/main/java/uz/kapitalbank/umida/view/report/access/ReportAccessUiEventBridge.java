package uz.kapitalbank.umida.view.report.access;

import io.jmix.flowui.UiEventPublisher;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.service.report.ReportAccessChangedEvent;

/**
 * Hands a change of the access to the reports over to the open screens of every user: the menu item and the
 * screen of the access management count the pending requests again, see
 * {@link uz.kapitalbank.umida.view.main.MainView} and {@link ReportAccessManagementView}. The service knows
 * nothing of the screens, so the bridge lives here.
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

    /** The pending requests may have changed; received by the views with an {@code @EventListener}. */
    public static class RequestsChangedUiEvent extends ApplicationEvent {

        public RequestsChangedUiEvent(Object source) {
            super(source);
        }
    }
}
