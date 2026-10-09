package uz.kapitalbank.umida.service.dq;

import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.MetadataTools;
import io.jmix.core.UnconstrainedDataManager;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.dq.DqCheckRun;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.enums.dq.DqCheckRunTrigger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who started a {@link DqCheckRun}. A manual run records the employee the user is linked to
 * ({@code User.employee}); the user is shown, found back through that employee.
 * <p>
 * Users are read unconstrained: the initiator of a run is part of the run's own record, and a user
 * allowed to see the runs need not be allowed to browse the users.
 */
@Service
public class DqCheckRunService {

    private final UnconstrainedDataManager dataManager;
    private final MetadataTools metadataTools;
    private final Messages messages;

    public DqCheckRunService(UnconstrainedDataManager dataManager, MetadataTools metadataTools, Messages messages) {
        this.dataManager = dataManager;
        this.metadataTools = metadataTools;
        this.messages = messages;
    }

    /**
     * The employee the user is linked to; empty for a user without one, or one that exists only in
     * memory, such as {@code system}.
     */
    public Optional<OrgStructureEmployee> findEmployeeOfUser(String username) {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", username)
                .fetchPlan(fp -> fp.add("employee", FetchPlan.BASE))
                .optional()
                .map(User::getEmployee);
    }

    /**
     * The "triggered by" caption of each run, keyed by run id: the title of
     * {@link DqCheckRunTrigger#SYSTEM} for a system run; for a manual one, the instance name of the
     * user linked to the employee who started it, or of the employee when no user is linked to it any
     * more. A run with no initiator recorded gets an empty caption.
     *
     * @param runs runs loaded with {@code triggeredBy}; the users of all of them are read in one query
     */
    public Map<UUID, String> getTriggeredByCaptions(Collection<DqCheckRun> runs) {
        List<OrgStructureEmployee> employees = runs.stream()
                .filter(run -> run.getTriggered() == DqCheckRunTrigger.MANUAL)
                .map(DqCheckRun::getTriggeredBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, String> userNamesByEmployee = loadUserNamesByEmployee(employees);

        Map<UUID, String> captions = new HashMap<>();
        for (DqCheckRun run : runs) {
            captions.put(run.getId(), triggeredByCaption(run, userNamesByEmployee));
        }
        return captions;
    }

    private String triggeredByCaption(DqCheckRun run, Map<String, String> userNamesByEmployee) {
        DqCheckRunTrigger triggered = run.getTriggered();
        if (triggered == DqCheckRunTrigger.SYSTEM) {
            return messages.getMessage(triggered);
        }
        OrgStructureEmployee employee = run.getTriggeredBy();
        if (triggered != DqCheckRunTrigger.MANUAL || employee == null) {
            return "";
        }
        String userName = userNamesByEmployee.get(employee.getId());
        return userName != null ? userName : metadataTools.getInstanceName(employee);
    }

    private Map<String, String> loadUserNamesByEmployee(List<OrgStructureEmployee> employees) {
        if (employees.isEmpty()) {
            return Map.of();
        }
        List<User> users = dataManager.load(User.class)
                .query("select u from umida_User u where u.employee in :employees")
                .parameter("employees", employees)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.INSTANCE_NAME)
                        .add("employee", FetchPlan.INSTANCE_NAME))
                .list();

        Map<String, String> namesByEmployee = new HashMap<>();
        // one employee belongs to one user at most, see User.employee
        users.forEach(user -> namesByEmployee.put(user.getEmployee().getId(), metadataTools.getInstanceName(user)));
        return namesByEmployee;
    }
}
