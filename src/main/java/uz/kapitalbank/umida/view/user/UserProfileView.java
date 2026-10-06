package uz.kapitalbank.umida.view.user;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.FileRef;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import uz.kapitalbank.umida.service.user.UserAvatars;
import uz.kapitalbank.umida.service.user.UserProfileService;
import uz.kapitalbank.umida.view.main.MainView;

/**
 * Profile of the logged-in user, opened from the user menu. Shows the employee the user is linked to and lets the
 * user upload or remove the profile photo, which the org chart shows instead of the employee photo.
 */
@Route(value = "profile", layout = MainView.class)
@ViewController(id = "umida_UserProfileView")
@ViewDescriptor(path = "user-profile-view.xml")
public class UserProfileView extends StandardView {

    @ViewComponent
    private Avatar photoAvatar;
    @ViewComponent
    private H3 displayNameLabel;
    @ViewComponent
    private Span usernameLabel;
    @ViewComponent
    private JmixButton removePhotoButton;
    @ViewComponent
    private FormLayout employeeForm;
    @ViewComponent
    private TypedTextField<String> employeeNameField;
    @ViewComponent
    private TypedTextField<String> personnelNumberField;
    @ViewComponent
    private TypedTextField<String> jobTitleField;
    @ViewComponent
    private TypedTextField<String> subdivisionField;
    @ViewComponent
    private TypedTextField<String> emailField;
    @ViewComponent
    private Span noEmployeeLabel;

    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private UserAvatars userAvatars;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        showProfile(userProfileService.getCurrentUserProfile());
    }

    @Subscribe("photoUploadField")
    public void onPhotoUploadFieldComponentValueChange(
            final AbstractField.ComponentValueChangeEvent<FileStorageUploadField, FileRef> event) {
        // the field only uploads: a file put into the storage becomes the profile photo
        if (event.getValue() != null) {
            showProfile(userProfileService.updateCurrentUserProfilePhoto(event.getValue()));
        }
    }

    @Subscribe(id = "removePhotoButton", subject = "clickListener")
    public void onRemovePhotoButtonClick(final ClickEvent<JmixButton> event) {
        showProfile(userProfileService.updateCurrentUserProfilePhoto(null));
    }

    private void showProfile(UserProfileService.UserProfile profile) {
        displayNameLabel.setText(profile.displayName().isEmpty() ? profile.username() : profile.displayName());
        usernameLabel.setText(profile.username());
        userAvatars.showPhoto(photoAvatar, profile);
        removePhotoButton.setEnabled(profile.profilePhoto() != null);

        UserProfileService.EmployeeInfo employee = profile.employee();
        employeeForm.setVisible(employee != null);
        noEmployeeLabel.setVisible(employee == null);
        if (employee != null) {
            employeeNameField.setTypedValue(employee.fullName());
            personnelNumberField.setTypedValue(employee.personnelNumber());
            jobTitleField.setTypedValue(employee.jobTitle());
            subdivisionField.setTypedValue(employee.subdivision());
            emailField.setTypedValue(employee.email() != null ? employee.email() : profile.email());
        }
    }
}
