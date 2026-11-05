package uz.kapitalbank.umida.view.report.access;

import com.vaadin.flow.component.html.Span;
import io.jmix.core.Messages;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.enums.report.ReportAccessGrantStatus;
import uz.kapitalbank.umida.enums.report.ReportAccessRequestStatus;

import java.util.List;

/**
 * Where a grant or a request stands, as a Lumo badge coloured by what it means: the same in the access
 * management ({@link ReportAccessManagementView}) and in the requests of an employee
 * ({@link ReportAccessRequestsView}).
 */
@Component
public class ReportAccessStatusBadges {

    private final Messages messages;

    public ReportAccessStatusBadges(Messages messages) {
        this.messages = messages;
    }

    public Span grant(ReportAccessGrantStatus status) {
        return badge(status, switch (status) {
            // solid: the theme's success text colour is white, unreadable on the light success background
            case ACTIVE -> "success primary";
            case REVOKED -> "error";
            case DISMISSED -> "warning";
            case EXPIRED -> "contrast";
            case NOT_STARTED -> null; // the plain badge
        });
    }

    public Span request(@Nullable ReportAccessRequestStatus status) {
        if (status == null) {
            return new Span();
        }
        return badge(status, switch (status) {
            case PENDING -> "primary";
            case APPROVED -> "success primary";
            case REJECTED -> "error";
            case CANCELLED -> "contrast";
        });
    }

    private Span badge(Enum<?> status, @Nullable String color) {
        Span badge = new Span(messages.getMessage(status));
        badge.getElement().getThemeList().addAll(List.of("badge", "small", "pill"));
        if (color != null) {
            badge.getElement().getThemeList().addAll(List.of(color.split(" ")));
        }
        return badge;
    }
}
