package uz.kapitalbank.umida.service.report;

import io.jmix.core.EntityStates;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.reports.entity.Report;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Generates the code on every save path of the add-on: the editor, the wizard, the import and the
 * copy, which clears the code of the copy. The check is for a code rather than for a new report, as
 * the copy is saved as a detached instance. A report that has a code keeps it.
 *
 * <p>Jmix publishes the event for the replaced entity, so it is {@link Report} here, not
 * {@link uz.kapitalbank.umida.entity.report.ReportCustom}: an event of the replacing one never comes.
 */
@Component
public class ReportCustomEventListener {

    private final ReportCustomService reportCustomService;
    private final EntityStates entityStates;

    public ReportCustomEventListener(ReportCustomService reportCustomService, EntityStates entityStates) {
        this.reportCustomService = reportCustomService;
        this.entityStates = entityStates;
    }

    @EventListener
    public void onReportSaving(final EntitySavingEvent<Report> event) {
        Report report = event.getEntity();
        if (entityStates.isLoaded(report, "code") && (report.getCode() == null || report.getCode().isBlank())) {
            report.setCode(reportCustomService.generateCode());
        }
    }
}
