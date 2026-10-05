package uz.kapitalbank.umida.service.orgstructure;

import io.jmix.core.FetchPlan;
import io.jmix.core.FetchPlanBuilder;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.ldap.LdapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;

import java.time.LocalDate;
import java.util.*;

/**
 * Links a {@link User} to its HR record, {@link OrgStructureEmployee}, and keeps the user's {@code active}
 * flag in line with the employee's dismissal.
 * <p>
 * An LDAP username is the employee's AD account, so the link is found by
 * {@code OrgStructureEmployee.adAccount}, compared case-insensitively. Once made, the link points at the
 * employee id, which never changes, so it survives a later change of the AD account.
 * <p>
 * A dismissed employee ({@code dismissalDate} today or earlier) turns the linked user inactive. The reverse
 * never happens automatically: a user deactivated here is activated again only by an administrator, so that a
 * user an administrator switched off on purpose cannot come back through a corrected HR record.
 * <p>
 * Runs unconstrained: it is called during the LDAP login, before the user has any permission, and from a
 * Quartz job, which has no user at all.
 */
@Service
public class EmployeeUserLinkService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeUserLinkService.class);

    private final UnconstrainedDataManager dataManager;
    private final TimeSource timeSource;
    private final LdapProperties ldapProperties;

    public EmployeeUserLinkService(UnconstrainedDataManager dataManager,
                                   TimeSource timeSource,
                                   LdapProperties ldapProperties) {
        this.dataManager = dataManager;
        this.timeSource = timeSource;
        this.ldapProperties = ldapProperties;
    }

    /**
     * Links the user to the employee whose AD account equals the username, and deactivates the user when the
     * linked employee is dismissed. Called on every LDAP login.
     * <p>
     * An employee already linked to another user is left alone (logged), as is the current link when no
     * employee carries the AD account any more.
     *
     * @return the user as saved, or {@code null} when there is no user with this username
     */
    @Nullable
    public User linkByAdAccount(String username) {
        User user = dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", username)
                .fetchPlan(this::userWithEmployee)
                .optional()
                .orElse(null);
        if (user == null) {
            return null;
        }

        boolean changed = linkEmployee(user, findEmployeeByAdAccount(username).orElse(null));
        changed |= deactivateIfDismissed(user, today());

        return changed ? dataManager.save(user) : user;
    }

    /**
     * Finds the employee by AD account, case-insensitively.
     */
    public Optional<OrgStructureEmployee> findEmployeeByAdAccount(@Nullable String adAccount) {
        if (adAccount == null || adAccount.isBlank()) {
            return Optional.empty();
        }
        return pickEmployee(dataManager.load(OrgStructureEmployee.class)
                .query("select e from umida_OrgStructureEmployee e where lower(trim(e.adAccount)) = :adAccount")
                .parameter("adAccount", normalizeAccount(adAccount))
                .list());
    }

    /**
     * Whether the employee is linked to a user other than {@code user}. A {@code null} user (one not saved
     * yet) counts as other than everyone.
     */
    public boolean isLinkedToOtherUser(OrgStructureEmployee employee, @Nullable User user) {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.employee = :employee")
                .parameter("employee", employee)
                .list()
                .stream()
                .anyMatch(linked -> user == null || !linked.getId().equals(user.getId()));
    }

    /**
     * Brings every user in line with HR: links the users that have not logged in since the link was
     * introduced (they would otherwise never be found by the dismissal check), then deactivates the users of
     * the dismissed employees. Called by {@code DismissedEmployeeUsersJob}.
     */
    public SyncResult syncUsersWithEmployees() {
        int linked = linkUnlinkedUsers();
        int deactivated = deactivateDismissedEmployeeUsers();
        return new SyncResult(linked, deactivated);
    }

    /**
     * Links the users without an employee to the employee of the same AD account. The users that log in with
     * the database password ({@code jmix.ldap.standard-authentication-users}: admin, system) are not LDAP
     * accounts and are skipped; an administrator may still link them by hand.
     *
     * @return the number of users linked
     */
    public int linkUnlinkedUsers() {
        Set<String> standardUsers = new HashSet<>(ldapProperties.getStandardAuthenticationUsers());
        List<User> unlinked = dataManager.load(User.class)
                .query("select u from umida_User u where u.employee is null")
                .fetchPlan(this::userWithEmployee)
                .list()
                .stream()
                .filter(user -> !standardUsers.contains(user.getUsername()))
                .toList();
        if (unlinked.isEmpty()) {
            return 0;
        }

        Map<String, List<OrgStructureEmployee>> employeesByAccount = new HashMap<>();
        dataManager.load(OrgStructureEmployee.class)
                .query("select e from umida_OrgStructureEmployee e where e.adAccount is not null")
                .list()
                .forEach(e -> employeesByAccount
                        .computeIfAbsent(normalizeAccount(e.getAdAccount()), key -> new ArrayList<>())
                        .add(e));
        Set<String> linkedEmployeeIds = new HashSet<>(dataManager.loadValue(
                        "select u.employee.id from umida_User u where u.employee is not null", String.class)
                .list());

        SaveContext saveContext = new SaveContext();
        for (User user : unlinked) {
            pickEmployee(employeesByAccount.getOrDefault(normalizeAccount(user.getUsername()), List.of()))
                    .ifPresent(employee -> link(user, employee, linkedEmployeeIds, saveContext));
        }
        return save(saveContext);
    }

    /**
     * Deactivates the active users whose employee is dismissed today or earlier.
     *
     * @return the number of users deactivated
     */
    public int deactivateDismissedEmployeeUsers() {
        LocalDate today = today();
        List<User> users = dataManager.load(User.class)
                .query("select u from umida_User u where u.active = true and u.employee.dismissalDate <= :today")
                .parameter("today", today)
                .fetchPlan(this::userWithEmployee)
                .list();

        SaveContext saveContext = new SaveContext();
        for (User user : users) {
            if (deactivateIfDismissed(user, today)) {
                saveContext.saving(user);
            }
        }
        return save(saveContext);
    }

    /**
     * Should HR hold several records of one AD account, a current employee wins over a dismissed one, then
     * the latest hire.
     */
    private Optional<OrgStructureEmployee> pickEmployee(List<OrgStructureEmployee> candidates) {
        LocalDate today = today();
        return candidates.stream()
                .min(Comparator
                        .comparing((OrgStructureEmployee e) -> isDismissed(e, today))
                        .thenComparing(OrgStructureEmployee::getHireDate,
                                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /** @return the number of entities saved */
    private int save(SaveContext saveContext) {
        int count = saveContext.getEntitiesToSave().size();
        if (count > 0) {
            dataManager.save(saveContext);
        }
        return count;
    }

    private String normalizeAccount(String adAccount) {
        return adAccount.trim().toLowerCase(Locale.ROOT);
    }

    private void link(User user, OrgStructureEmployee employee, Set<String> linkedEmployeeIds,
                      SaveContext saveContext) {
        if (!linkedEmployeeIds.add(employee.getId())) {
            log.warn("Employee {} ({}) is already linked to another user, user {} is left unlinked",
                    employee.getId(), employee.getAdAccount(), user.getUsername());
            return;
        }
        user.setEmployee(employee);
        saveContext.saving(user);
        log.info("User {} linked to employee {}", user.getUsername(), employee.getId());
    }

    /** @return whether the link changed */
    private boolean linkEmployee(User user, @Nullable OrgStructureEmployee employee) {
        if (employee == null || employee.equals(user.getEmployee())) {
            return false;
        }
        if (isLinkedToOtherUser(employee, user)) {
            log.warn("Employee {} ({}) is already linked to another user, user {} keeps its current link",
                    employee.getId(), employee.getAdAccount(), user.getUsername());
            return false;
        }
        user.setEmployee(employee);
        log.info("User {} linked to employee {}", user.getUsername(), employee.getId());
        return true;
    }

    /** @return whether the user was deactivated */
    private boolean deactivateIfDismissed(User user, LocalDate today) {
        OrgStructureEmployee employee = user.getEmployee();
        if (employee == null || !Boolean.TRUE.equals(user.getActive()) || !isDismissed(employee, today)) {
            return false;
        }
        user.setActive(false);
        log.info("User {} deactivated: employee {} dismissed on {}",
                user.getUsername(), employee.getId(), employee.getDismissalDate());
        return true;
    }

    /**
     * The dismissal date is the last day of the employee's access: a bank cuts it on that very day, not the
     * day after.
     */
    private boolean isDismissed(OrgStructureEmployee employee, LocalDate today) {
        return employee.getDismissalDate() != null && !employee.getDismissalDate().isAfter(today);
    }

    private LocalDate today() {
        return timeSource.now().toLocalDate();
    }

    private void userWithEmployee(FetchPlanBuilder fetchPlan) {
        fetchPlan.addFetchPlan(FetchPlan.BASE)
                .add("employee", FetchPlan.BASE);
    }

    /** Outcome of {@link #syncUsersWithEmployees()}. */
    public record SyncResult(int linked, int deactivated) {
    }
}
