package uz.kapitalbank.umida.security;

import uz.kapitalbank.umida.entity.User;
import io.jmix.securitydata.user.AbstractDatabaseUserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Looks a user up by username case-insensitively.
 * <p>
 * The LDAP bind ignores the case of the AD account, so {@code yuliya.kim}, {@code YULIYA.KIM} and
 * {@code Yuliya.Kim} all log in as the same directory user. An exact lookup would miss the existing user for
 * every spelling but the first and let the LDAP synchronization create a new one each time.
 */
@Primary
@Component("umida_UserRepository")
public class DatabaseUserRepository extends AbstractDatabaseUserRepository<User> {

    /**
     * The form a username is stored and compared in: trimmed and lower-cased.
     */
    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    protected Class<User> getUserClass() {
        return User.class;
    }

    @Override
    protected List<User> loadUsersByUsernameFromDatabase(final String username) {
        return dataManager.load(User.class)
                .query("where lower(e.username) = :username")
                .parameter("username", normalizeUsername(username))
                .list();
    }

    @Override
    protected void initSystemUser(final User systemUser) {
        final Collection<GrantedAuthority> authorities = getGrantedAuthoritiesBuilder()
                .addResourceRole(FullAccessRole.CODE)
                .build();
        systemUser.setAuthorities(authorities);
    }

    @Override
    protected void initAnonymousUser(final User anonymousUser) {
    }
}
