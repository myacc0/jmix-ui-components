package uz.kapitalbank.umida.security;

import io.jmix.ldap.userdetails.AbstractLdapUserDetailsSynchronizationStrategy;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.orgstructure.EmployeeUserLinkService;

import java.util.Collection;

/**
 * Copies the LDAP attributes of a user into {@link User} on every LDAP login, creating the user on the first one,
 * and links the user to its employee record by the AD account ({@link EmployeeUserLinkService}).
 * <p>
 * Also rejects the login of an inactive user. Unlike the database login, the LDAP one never checks
 * {@code User.active} by itself — Spring's LDAP authentication provider skips the account status checks — so
 * without this a user deactivated in the application, e.g. after the employee's dismissal, would still get in
 * for as long as the LDAP account lives.
 * <p>
 * The username typed at login is normalized ({@link DatabaseUserRepository#normalizeUsername}): the LDAP bind
 * accepts the AD account in any case, and the user must come out the same whatever the case typed.
 * <p>
 * Role assignments are not synchronized ({@code jmix.ldap.synchronize-role-assignments=false}):
 * they stay managed in the application.
 */
@Component("umida_LdapUserSynchronizationStrategy")
public class LdapUserSynchronizationStrategy extends AbstractLdapUserDetailsSynchronizationStrategy<User> {

    private final EmployeeUserLinkService employeeUserLinkService;

    public LdapUserSynchronizationStrategy(EmployeeUserLinkService employeeUserLinkService) {
        this.employeeUserLinkService = employeeUserLinkService;
    }

    @Override
    public UserDetails synchronizeUserDetails(
            final DirContextOperations ctx,
            final String username,
            final Collection<? extends GrantedAuthority> authorities
    ) {
        UserDetails synchronizedUser = super.synchronizeUserDetails(
                ctx, DatabaseUserRepository.normalizeUsername(username), authorities);
        User user = employeeUserLinkService.linkByAdAccount(synchronizedUser.getUsername());
        if (user != null && !user.isEnabled()) {
            throw new DisabledException("User " + username + " is inactive");
        }
        return user != null ? user : synchronizedUser;
    }

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    @Override
    protected void mapUserDetailsAttributes(final User userDetails, final DirContextOperations ctx) {
        userDetails.setFirstName(ctx.getStringAttribute("givenName"));
        userDetails.setLastName(ctx.getStringAttribute("sn"));
        userDetails.setEmail(ctx.getStringAttribute("mail"));
    }
}
