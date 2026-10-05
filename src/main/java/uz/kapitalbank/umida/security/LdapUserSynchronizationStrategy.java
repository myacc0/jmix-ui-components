package uz.kapitalbank.umida.security;

import io.jmix.ldap.userdetails.AbstractLdapUserDetailsSynchronizationStrategy;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.stereotype.Component;
import uz.kapitalbank.umida.entity.User;

/**
 * Copies the LDAP attributes of a user into {@link User} on every LDAP login, creating the user on the first one.
 * <p>
 * Role assignments are not synchronized ({@code jmix.ldap.synchronize-role-assignments=false}):
 * they stay managed in the application.
 */
@Component("umida_LdapUserSynchronizationStrategy")
public class LdapUserSynchronizationStrategy extends AbstractLdapUserDetailsSynchronizationStrategy<User> {

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
