package uz.kapitalbank.umida.user;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestAuthenticator;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import uz.kapitalbank.umida.UmidaApplication;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.view.user.UserProfileView;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The profile view of the logged-in user: it opens, shows the user, and uploading or removing the photo changes
 * {@code User.picture}.
 * <p>
 * Runs as {@code admin}: the default {@code @UiTest} user, {@code system}, is not stored in the database and so
 * has no picture to change.
 */
@UiTest(authenticator = UserProfileUiTest.AdminAuthenticator.class)
@SpringBootTest(classes = {UmidaApplication.class, FlowuiTestAssistConfiguration.class})
class UserProfileUiTest {

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    private FileRef originalPicture;
    private FileRef uploaded;

    @BeforeEach
    void setUp() {
        originalPicture = loadAdmin().getPicture();
    }

    @Test
    void uploadsAndRemovesTheProfilePhoto() {
        viewNavigators.view(UiTestUtils.getCurrentView(), UserProfileView.class).navigate();
        UserProfileView view = UiTestUtils.getCurrentView();

        H3 displayNameLabel = UiTestUtils.getComponent(view, "displayNameLabel");
        Span usernameLabel = UiTestUtils.getComponent(view, "usernameLabel");
        assertThat(displayNameLabel.getText()).isNotBlank();
        assertThat(usernameLabel.getText()).isEqualTo("admin");

        // admin is a database account, not linked to an employee unless an administrator did it by hand
        FormLayout employeeForm = UiTestUtils.getComponent(view, "employeeForm");
        Span noEmployeeLabel = UiTestUtils.getComponent(view, "noEmployeeLabel");
        boolean linked = loadAdmin().getEmployee() != null;
        assertThat(employeeForm.isVisible()).isEqualTo(linked);
        assertThat(noEmployeeLabel.isVisible()).isEqualTo(!linked);

        // what the upload field does once the file is in the storage
        FileStorage fileStorage = fileStorageLocator.getDefault();
        uploaded = fileStorage.saveStream("profile-ui-test.png", new ByteArrayInputStream("png".getBytes()));
        FileStorageUploadField photoUploadField = UiTestUtils.getComponent(view, "photoUploadField");
        photoUploadField.setValue(uploaded);

        JmixButton removePhotoButton = UiTestUtils.getComponent(view, "removePhotoButton");
        assertThat(loadAdmin().getPicture()).isEqualTo(uploaded);
        assertThat(removePhotoButton.isEnabled()).isTrue();

        removePhotoButton.click();

        assertThat(loadAdmin().getPicture()).isNull();
        assertThat(removePhotoButton.isEnabled()).isFalse();
    }

    public static class AdminAuthenticator implements UiTestAuthenticator {

        @Override
        public void setupAuthentication(ApplicationContext context) {
            context.getBean(SystemAuthenticator.class).begin("admin");
        }

        @Override
        public void removeAuthentication(ApplicationContext context) {
            context.getBean(SystemAuthenticator.class).end();
        }
    }

    private User loadAdmin() {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = 'admin'")
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("employee", "_instance_name"))
                .one();
    }

    @AfterEach
    void tearDown() {
        User admin = loadAdmin();
        admin.setPicture(originalPicture);
        dataManager.save(admin);
        if (uploaded != null) {
            fileStorageLocator.getDefault().removeFile(uploaded);
        }
    }
}
