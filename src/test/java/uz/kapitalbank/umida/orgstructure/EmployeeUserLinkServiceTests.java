package uz.kapitalbank.umida.orgstructure;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.service.orgstructure.EmployeeUserLinkService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Linking a user to its employee by the AD account and deactivating the users of dismissed employees.
 * <p>
 * Every fixture account is unique, so the real users and employees of the database never match one.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class EmployeeUserLinkServiceTests {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EmployeeUserLinkService employeeUserLinkService;

    private final List<User> users = new ArrayList<>();
    private final List<OrgStructureEmployee> employees = new ArrayList<>();

    @Test
    void linksUserToEmployeeOfTheSameAdAccountIgnoringCase() {
        String account = uniqueAccount();
        OrgStructureEmployee employee = createEmployee(account.toUpperCase(), null);
        User user = createUser(account, null);

        employeeUserLinkService.linkByAdAccount(account);

        User reloaded = reload(user);
        assertThat(reloaded.getEmployee()).isEqualTo(employee);
        assertThat(reloaded.getActive()).isTrue();
    }

    @Test
    void leavesEmployeeLinkedToAnotherUserAlone() {
        String account = uniqueAccount();
        OrgStructureEmployee employee = createEmployee(account, null);
        User owner = createUser("owner-" + account, employee);
        User user = createUser(account, null);

        employeeUserLinkService.linkByAdAccount(account);

        assertThat(reload(user).getEmployee()).isNull();
        assertThat(reload(owner).getEmployee()).isEqualTo(employee);
    }

    @Test
    void prefersCurrentEmployeeOverDismissedOneOfTheSameAccount() {
        String account = uniqueAccount();
        createEmployee(account, LocalDate.now().minusYears(1));
        OrgStructureEmployee current = createEmployee(account, null);

        assertThat(employeeUserLinkService.findEmployeeByAdAccount(account)).contains(current);
    }

    @Test
    void deactivatesUserOnLinkWhenEmployeeIsAlreadyDismissed() {
        String account = uniqueAccount();
        createEmployee(account, LocalDate.now().minusDays(1));
        User user = createUser(account, null);

        User linked = employeeUserLinkService.linkByAdAccount(account);

        assertThat(linked).isNotNull();
        assertThat(linked.isEnabled()).isFalse();
        assertThat(reload(user).getActive()).isFalse();
    }

    @Test
    void deactivatesUsersOfEmployeesDismissedTodayOrEarlier() {
        User dismissedToday = createUser(uniqueAccount(), createEmployee(uniqueAccount(), LocalDate.now()));
        User dismissedEarlier = createUser(uniqueAccount(),
                createEmployee(uniqueAccount(), LocalDate.now().minusMonths(1)));
        User dismissedLater = createUser(uniqueAccount(),
                createEmployee(uniqueAccount(), LocalDate.now().plusDays(1)));
        User notDismissed = createUser(uniqueAccount(), createEmployee(uniqueAccount(), null));

        int deactivated = employeeUserLinkService.deactivateDismissedEmployeeUsers();

        assertThat(deactivated).isGreaterThanOrEqualTo(2);
        assertThat(reload(dismissedToday).getActive()).isFalse();
        assertThat(reload(dismissedEarlier).getActive()).isFalse();
        assertThat(reload(dismissedLater).getActive()).isTrue();
        assertThat(reload(notDismissed).getActive()).isTrue();
    }

    @Test
    void doesNotReactivateUserWhenDismissalIsWithdrawn() {
        OrgStructureEmployee employee = createEmployee(uniqueAccount(), LocalDate.now().minusDays(1));
        User user = createUser(employee.getAdAccount(), employee);
        employeeUserLinkService.deactivateDismissedEmployeeUsers();

        employee.setDismissalDate(null);
        employees.set(employees.indexOf(employee), dataManager.save(employee));
        employeeUserLinkService.syncUsersWithEmployees();
        employeeUserLinkService.linkByAdAccount(user.getUsername());

        assertThat(reload(user).getActive()).isFalse();
    }

    @Test
    void syncLinksUsersThatHaveNotLoggedInAndDeactivatesTheDismissedOnes() {
        String activeAccount = uniqueAccount();
        String dismissedAccount = uniqueAccount();
        OrgStructureEmployee active = createEmployee(activeAccount, null);
        OrgStructureEmployee dismissed = createEmployee(dismissedAccount, LocalDate.now().minusDays(3));
        User activeUser = createUser(activeAccount, null);
        User dismissedUser = createUser(dismissedAccount, null);

        EmployeeUserLinkService.SyncResult result = employeeUserLinkService.syncUsersWithEmployees();

        assertThat(result.linked()).isGreaterThanOrEqualTo(2);
        assertThat(result.deactivated()).isGreaterThanOrEqualTo(1);
        assertThat(reload(activeUser).getEmployee()).isEqualTo(active);
        assertThat(reload(activeUser).getActive()).isTrue();
        assertThat(reload(dismissedUser).getEmployee()).isEqualTo(dismissed);
        assertThat(reload(dismissedUser).getActive()).isFalse();
    }

    @Test
    void reportsWhetherEmployeeIsLinkedToAnotherUser() {
        OrgStructureEmployee employee = createEmployee(uniqueAccount(), null);
        User owner = createUser(uniqueAccount(), employee);
        User other = createUser(uniqueAccount(), null);

        assertThat(employeeUserLinkService.isLinkedToOtherUser(employee, owner)).isFalse();
        assertThat(employeeUserLinkService.isLinkedToOtherUser(employee, other)).isTrue();
        assertThat(employeeUserLinkService.isLinkedToOtherUser(employee, null)).isTrue();
    }

    private String uniqueAccount() {
        return "link.test." + UUID.randomUUID();
    }

    private OrgStructureEmployee createEmployee(String adAccount, LocalDate dismissalDate) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Link Test " + adAccount);
        employee.setAdAccount(adAccount);
        employee.setHireDate(LocalDate.now().minusYears(2));
        employee.setDismissalDate(dismissalDate);
        OrgStructureEmployee saved = dataManager.save(employee);
        employees.add(saved);
        return saved;
    }

    private User createUser(String username, OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setEmployee(employee);
        User saved = dataManager.save(user);
        users.add(saved);
        return saved;
    }

    private User reload(User user) {
        return dataManager.load(User.class)
                .id(user.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee", FetchPlan.BASE))
                .one();
    }

    @AfterEach
    void tearDown() {
        // users first: they reference the employees
        users.forEach(user -> dataManager.remove(reload(user)));
        employees.forEach(dataManager::remove);
        users.clear();
        employees.clear();
    }
}
