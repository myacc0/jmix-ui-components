package uz.kapitalbank.umida.user;

import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.security.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sample integration test for the User entity.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
public class UserTest {

    @Autowired
    DataManager dataManager;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserRepository userRepository;

    User savedUser;

    @Test
    void test_saveAndLoad() {
        // Create and save a new User
        User user = dataManager.create(User.class);
        user.setUsername("test-user-" + System.currentTimeMillis());
        user.setPassword(passwordEncoder.encode("test-passwd"));
        savedUser = dataManager.save(user);

        // Check the new user can be loaded
        User loadedUser = dataManager.load(User.class).id(user.getId()).one();
        assertThat(loadedUser).isEqualTo(user);

        // Check the new user is available through UserRepository
        UserDetails userDetails = userRepository.loadUserByUsername(user.getUsername());
        assertThat(userDetails).isEqualTo(user);
    }

    @Test
    void userIsFoundByUsernameInAnyCase() {
        User user = dataManager.create(User.class);
        user.setUsername("test-user-" + System.currentTimeMillis());
        savedUser = dataManager.save(user);

        UserDetails userDetails = userRepository.loadUserByUsername(user.getUsername().toUpperCase(Locale.ROOT));
        assertThat(userDetails).isEqualTo(user);
    }

    @Test
    void usernameDifferingOnlyInCaseIsRejected() {
        User user = dataManager.create(User.class);
        user.setUsername("test-user-" + System.currentTimeMillis());
        savedUser = dataManager.save(user);

        User duplicate = dataManager.create(User.class);
        duplicate.setUsername(user.getUsername().toUpperCase(Locale.ROOT));
        assertThatThrownBy(() -> dataManager.save(duplicate))
                .hasStackTraceContaining("idx_umida_user_on_lower_username");
    }

    @AfterEach
    void tearDown() {
        if (savedUser != null)
            dataManager.remove(savedUser);
    }
}
