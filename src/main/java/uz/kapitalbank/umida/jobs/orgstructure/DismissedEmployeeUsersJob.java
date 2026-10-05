package uz.kapitalbank.umida.jobs.orgstructure;

import io.jmix.core.security.SystemAuthenticator;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.service.orgstructure.EmployeeUserLinkService;

/**
 * Deactivates the users of the employees HR has dismissed ({@code OrgStructureEmployee.dismissalDate}), linking
 * first the users that have not logged in since the user-employee link appeared. Scheduled by
 * {@link DismissedEmployeeUsersJobConfiguration}.
 */
@DisallowConcurrentExecution
public class DismissedEmployeeUsersJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(DismissedEmployeeUsersJob.class);

    @Autowired
    private EmployeeUserLinkService employeeUserLinkService;
    @Autowired
    private SystemAuthenticator systemAuthenticator;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // the scheduler thread carries no authentication
            EmployeeUserLinkService.SyncResult result =
                    systemAuthenticator.withSystem(employeeUserLinkService::syncUsersWithEmployees);
            log.info("DismissedEmployeeUsersJob finished: linked={}, deactivated={}",
                    result.linked(), result.deactivated());
        } catch (Exception e) {
            log.error("DismissedEmployeeUsersJob failed", e);
            throw new JobExecutionException("Failed to deactivate the users of dismissed employees", e);
        }
    }
}
