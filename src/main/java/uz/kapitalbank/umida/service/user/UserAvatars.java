package uz.kapitalbank.umida.service.user;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.server.streams.DownloadHandler;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.component.UiComponentUtils;
import org.springframework.stereotype.Component;

/**
 * Puts the photo of a user into an {@link Avatar}, with the same precedence as the org chart: the profile photo
 * the user uploaded, then the photo of the employee the user is linked to, then the initials of the name.
 */
@Component("umida_UserAvatars")
public class UserAvatars {

    private final FileStorageLocator fileStorageLocator;

    public UserAvatars(FileStorageLocator fileStorageLocator) {
        this.fileStorageLocator = fileStorageLocator;
    }

    public void showPhoto(Avatar avatar, UserProfileService.UserProfile profile) {
        avatar.setName(profile.displayName().isEmpty() ? profile.username() : profile.displayName());

        // the profile photo is streamed straight from the storage: a user not linked to an employee
        // has no photo URL to point at
        if (profile.picture() != null
                && UiComponentUtils.createResource(profile.picture(), fileStorageLocator)
                instanceof DownloadHandler downloadHandler) {
            avatar.setImageHandler(downloadHandler);
        } else if (profile.employee() != null && profile.employee().photoUrl() != null) {
            avatar.setImage(profile.employee().photoUrl());
        } else {
            avatar.setImage(null);
        }
    }
}
