package uz.kapitalbank.umida.security;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.ldap.LdapProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the LDAP login does beyond the bind: linking the user to its employee, refusing an inactive user, and
 * ending the sessions of a user once deactivated.
 * <p>
 * The strategy is called directly with a hand-made LDAP entry, so the fixture users need no entry in the LDIF.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class LdapUserSynchronizationStrategyTest {

    @Autowired
    private LdapUserSynchronizationStrategy strategy;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SessionRegistry sessionRegistry;
    @Autowired
    private LdapProperties ldapProperties;

    private final List<String> usernames = new ArrayList<>();
    private final List<OrgStructureEmployee> employees = new ArrayList<>();
    private final List<String> sessionIds = new ArrayList<>();

    @Test
    void firstLoginCreatesUserLinkedToTheEmployee() {
        String username = uniqueUsername();
        OrgStructureEmployee employee = createEmployee(username, null);

        strategy.synchronizeUserDetails(ldapEntry(), username, List.of());

        User user = findUser(username).orElseThrow();
        assertThat(user.getEmployee()).isEqualTo(employee);
        assertThat(user.getLastName()).isEqualTo("Тестов");
        assertThat(user.getActive()).isTrue();
    }

    @Test
    void firstLoginStoresTheUsernameInLowerCase() {
        String username = uniqueUsername();
        OrgStructureEmployee employee = createEmployee(username, null);

        strategy.synchronizeUserDetails(ldapEntry(), username.toUpperCase(Locale.ROOT), List.of());

        assertThat(findUser(username).orElseThrow().getEmployee()).isEqualTo(employee);
    }

    @Test
    void existingUserIsLinkedOnTheFirstLoginAfterTheEmployeeAppears() {
        String username = uniqueUsername();
        strategy.synchronizeUserDetails(ldapEntry(), username, List.of());
        assertThat(findUser(username).orElseThrow().getEmployee()).isNull();

        OrgStructureEmployee employee = createEmployee(username, null);
        strategy.synchronizeUserDetails(ldapEntry(), username, List.of());

        assertThat(findUser(username).orElseThrow().getEmployee()).isEqualTo(employee);
    }

    /**
     * Active Directory: the user logs in with its userPrincipalName, the employee carries the sAMAccountName,
     * found in the LDAP entry under jmix.ldap.username-attribute.
     */
    @Test
    void userPrincipalNameLoginIsLinkedByTheAccountOfTheLdapEntry() {
        String account = "ldap.sync." + UUID.randomUUID();
        String userPrincipalName = account + "@kapitalbank.uz";
        usernames.add(userPrincipalName);
        OrgStructureEmployee employee = createEmployee(account, null);
        DirContextAdapter entry = ldapEntry();
        entry.setAttributeValue(ldapProperties.getUsernameAttribute(), account);

        strategy.synchronizeUserDetails(entry, userPrincipalName.toUpperCase(Locale.ROOT), List.of());

        assertThat(findUser(userPrincipalName).orElseThrow().getEmployee()).isEqualTo(employee);
    }

    @Test
    void userPrincipalNameLoginWithoutAccountInTheLdapEntryIsLinkedByTheNameBeforeTheAt() {
        String account = "ldap.sync." + UUID.randomUUID();
        String userPrincipalName = account + "@kapitalbank.uz";
        usernames.add(userPrincipalName);
        OrgStructureEmployee employee = createEmployee(account, null);

        strategy.synchronizeUserDetails(ldapEntry(), userPrincipalName, List.of());

        assertThat(findUser(userPrincipalName).orElseThrow().getEmployee()).isEqualTo(employee);
    }

    @Test
    void loginOfDismissedEmployeeIsRejectedAndUserDeactivated() {
        String username = uniqueUsername();
        createEmployee(username, LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> strategy.synchronizeUserDetails(ldapEntry(), username, List.of()))
                .isInstanceOf(DisabledException.class);

        assertThat(findUser(username).orElseThrow().getActive()).isFalse();
    }

    @Test
    void loginOfUserDeactivatedInTheApplicationIsRejected() {
        String username = uniqueUsername();
        User user = dataManager.create(User.class);
        user.setUsername(username);
        user.setActive(false);
        dataManager.save(user);
        usernames.add(username);

        assertThatThrownBy(() -> strategy.synchronizeUserDetails(ldapEntry(), username, List.of()))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void deactivationExpiresTheSessionsOfTheUser() {
        String username = uniqueUsername();
        strategy.synchronizeUserDetails(ldapEntry(), username, List.of());
        User user = findUser(username).orElseThrow();

        String sessionId = "test-session-" + UUID.randomUUID();
        sessionRegistry.registerNewSession(sessionId, user);
        sessionIds.add(sessionId);

        user.setActive(false);
        dataManager.save(user);

        SessionInformation session = sessionRegistry.getSessionInformation(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.isExpired()).isTrue();
    }

    private DirContextAdapter ldapEntry() {
        DirContextAdapter ctx = new DirContextAdapter();
        ctx.setAttributeValue("givenName", "Тест");
        ctx.setAttributeValue("sn", "Тестов");
        ctx.setAttributeValue("mail", "ldap.sync.test@example.com");
        return ctx;
    }

    private String uniqueUsername() {
        String username = "ldap.sync." + UUID.randomUUID();
        usernames.add(username);
        return username;
    }

    private OrgStructureEmployee createEmployee(String adAccount, LocalDate dismissalDate) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Ldap Sync Test " + adAccount);
        employee.setAdAccount(adAccount);
        employee.setDismissalDate(dismissalDate);
        OrgStructureEmployee saved = dataManager.save(employee);
        employees.add(saved);
        return saved;
    }

    private Optional<User> findUser(String username) {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", username)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee", FetchPlan.BASE))
                .optional();
    }

    @AfterEach
    void tearDown() {
        sessionIds.forEach(sessionRegistry::removeSessionInformation);
        usernames.forEach(username -> findUser(username).ifPresent(dataManager::remove));
        employees.forEach(dataManager::remove);
        sessionIds.clear();
        usernames.clear();
        employees.clear();
    }
}
