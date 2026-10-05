package uz.kapitalbank.umida.security;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.AuthenticationManagerSupplier;
import io.jmix.security.role.RoleGrantedAuthorityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.service.orgstructure.EmployeeUserLinkService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Login through the embedded LDAP server started from {@code ldap/umida-users.ldif}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class LdapAuthenticationTest {

    /** Password of every user in the test LDIF. */
    private static final String LDAP_PASSWORD = "password";

    @Autowired
    private AuthenticationManagerSupplier authenticationManagerSupplier;

    @Autowired
    private RoleGrantedAuthorityUtils roleGrantedAuthorityUtils;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmployeeUserLinkService employeeUserLinkService;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void ldapUserLogsInWithLdapPassword() {
        Authentication authentication = authenticate("yuliya.kim", LDAP_PASSWORD);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(User.class);
        User user = (User) authentication.getPrincipal();
        assertThat(user.getUsername()).isEqualTo("yuliya.kim");
        assertThat(user.getLastName()).isEqualTo("Ким");
        assertThat(user.getFirstName()).isEqualTo("Юлия");
        assertThat(user.getEmail()).isEqualTo("yuliya.kim@gmail.com");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains(roleGrantedAuthorityUtils.getDefaultRolePrefix() + UiMinimalRole.CODE);
    }

    @Test
    void ldapUserMissingInDatabaseIsCreatedOnFirstLogin() {
        String username = "sergey.burunov";
        boolean existedBefore = findUser(username).isPresent();

        authenticate(username, LDAP_PASSWORD);

        User user = findUser(username).orElseThrow();
        if (!existedBefore) {
            cleanup.add(user);
        }
        assertThat(user.getLastName()).isEqualTo("Бурунов");
        assertThat(user.getFirstName()).isEqualTo("Сергей");
        assertThat(user.getEmail()).isEqualTo("sergey.burunov@gmail.com");
    }

    @Test
    void ldapLoginLinksUserToTheEmployeeOfTheAdAccount() {
        String username = "madinakhon.vakhobova";
        boolean existedBefore = findUser(username).isPresent();
        // the HR data may be absent in a fresh database: then the test brings its own employee
        OrgStructureEmployee employee = employeeUserLinkService.findEmployeeByAdAccount(username)
                .orElseGet(() -> createEmployee(username));

        authenticate(username, LDAP_PASSWORD);

        User user = findUser(username).orElseThrow();
        if (!existedBefore) {
            cleanup.add(0, user);
        }
        assertThat(user.getEmployee()).isEqualTo(employee);
    }

    @Test
    void usernameCaseDoesNotCreateAnotherUser() {
        String username = "yuliya.kim";
        boolean existedBefore = findUser(username).isPresent();

        for (String typed : List.of("YULIYA.KIM", "Yuliya.Kim", " yuliya.kim ", username)) {
            User principal = (User) authenticate(typed, LDAP_PASSWORD).getPrincipal();
            assertThat(principal.getUsername()).isEqualTo(username);
        }

        List<User> users = dataManager.load(User.class)
                .query("select u from umida_User u where lower(u.username) = :username")
                .parameter("username", username)
                .list();
        if (!existedBefore) {
            cleanup.addAll(users);
        }
        assertThat(users).hasSize(1);
    }

    @Test
    void ldapUserWithWrongPasswordIsRejected() {
        assertThatThrownBy(() -> authenticate("yuliya.kim", "wrong-password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void unknownUserIsRejected() {
        assertThatThrownBy(() -> authenticate("no.such.user", LDAP_PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void databasePasswordIsRejectedForNonStandardUser() {
        User user = dataManager.create(User.class);
        user.setUsername("ldap-test-user-" + System.currentTimeMillis());
        user.setPassword(passwordEncoder.encode("db-password"));
        cleanup.add(dataManager.save(user));

        assertThatThrownBy(() -> authenticate(user.getUsername(), "db-password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void adminLogsInWithDatabasePassword() {
        Authentication authentication = authenticate("admin", "admin");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(((User) authentication.getPrincipal()).getUsername()).isEqualTo("admin");
    }

    @AfterEach
    void tearDown() {
        cleanup.forEach(dataManager::remove);
    }

    private Authentication authenticate(String username, String password) {
        AuthenticationManager authenticationManager = authenticationManagerSupplier.getAuthenticationManager();
        return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

    private Optional<User> findUser(String username) {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", username)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("employee", FetchPlan.BASE))
                .optional();
    }

    private OrgStructureEmployee createEmployee(String adAccount) {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Ldap Test " + adAccount);
        employee.setAdAccount(adAccount);
        OrgStructureEmployee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }
}
