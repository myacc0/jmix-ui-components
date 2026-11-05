package uz.kapitalbank.umida.service.report;

import org.springframework.context.ApplicationEvent;

/**
 * The access to the reports has changed: a request was made, decided or withdrawn, or grants were given or
 * revoked. Published by {@link ReportAccessService} after the change is saved, so that the screens showing
 * the pending requests can count them again.
 */
public class ReportAccessChangedEvent extends ApplicationEvent {

    public ReportAccessChangedEvent(Object source) {
        super(source);
    }
}
