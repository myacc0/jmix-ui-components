package uz.kapitalbank.umida.service.user;

import io.jmix.core.FetchPlan;
import io.jmix.core.FileRef;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureJobTitle;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructurePosition;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureSubdivision;
import uz.kapitalbank.umida.service.orgstructure.EmployeePhotoService;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * The profile of the logged-in user: who the user is in the org structure and the profile photo, which the user
 * changes here and which the org chart then shows instead of the employee photo.
 * <p>
 * Every user may see and change only their own profile, so this service works on the authenticated user — never
 * on a substituted one, never on an arbitrary id — and runs unconstrained: a user with nothing but
 * {@code ui-minimal} has no entity permission to read or save {@link User} through the regular data manager.
 */
@Service
public class UserProfileService {

    private final UnconstrainedDataManager dataManager;
    private final CurrentUserSubstitution currentUserSubstitution;
    private final EmployeePhotoService employeePhotoService;

    public UserProfileService(UnconstrainedDataManager dataManager,
                              CurrentUserSubstitution currentUserSubstitution,
                              EmployeePhotoService employeePhotoService) {
        this.dataManager = dataManager;
        this.currentUserSubstitution = currentUserSubstitution;
        this.employeePhotoService = employeePhotoService;
    }

    public UserProfile getCurrentUserProfile() {
        return getProfile(currentUsername());
    }

    /**
     * Returns the profile of the given user. Read-only, used to render the avatar of whatever user the main view
     * shows, the substituted one included. A user that exists only in memory, such as {@code system}, gets an
     * empty profile.
     */
    public UserProfile getProfile(String username) {
        User user = findUser(username).orElse(null);
        if (user == null) {
            return new UserProfile(username, "", null, null, null);
        }
        OrgStructureEmployee employee = user.getEmployee();
        return new UserProfile(user.getUsername(), user.getDisplayNameWithoutUsername(), user.getEmail(),
                user.getPicture(), employee != null ? toEmployeeInfo(employee) : null);
    }

    /**
     * Replaces the profile photo of the logged-in user; {@code null} removes it, and the org chart falls back to
     * the employee photo.
     *
     * @param picture a file already put into the file storage, e.g. by an upload field
     */
    public UserProfile updateCurrentUserPicture(@Nullable FileRef picture) {
        String username = currentUsername();
        User user = findUser(username)
                .orElseThrow(() -> new IllegalStateException("User " + username + " is not stored in the database"));
        if (!Objects.equals(user.getPicture(), picture)) {
            user.setPicture(picture);
            dataManager.save(user);
        }
        return getCurrentUserProfile();
    }

    private String currentUsername() {
        return currentUserSubstitution.getAuthenticatedUser().getUsername();
    }

    private Optional<User> findUser(String username) {
        return dataManager.load(User.class)
                .query("select u from umida_User u where u.username = :username")
                .parameter("username", username)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("employee", FetchPlan.BASE))
                .optional();
    }

    private EmployeeInfo toEmployeeInfo(OrgStructureEmployee employee) {
        Optional<OrgStructurePosition> position = findMainPosition(employee);
        return new EmployeeInfo(
                employee.getId(),
                employee.getFullName(),
                employee.getPersonnelNumber(),
                employee.getEmail(),
                position.map(OrgStructurePosition::getJobTitle).map(OrgStructureJobTitle::getName).orElse(null),
                position.map(OrgStructurePosition::getSubdivision).map(OrgStructureSubdivision::getName).orElse(null),
                employeePhotoService.getPhotoUrl(employee.getId()));
    }

    /** The head position when the employee holds one, otherwise any. */
    private Optional<OrgStructurePosition> findMainPosition(OrgStructureEmployee employee) {
        return dataManager.load(OrgStructurePosition.class)
                .query("select p from umida_OrgStructurePosition p where p.employee = :employee")
                .parameter("employee", employee)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("jobTitle", FetchPlan.INSTANCE_NAME)
                        .add("subdivision", FetchPlan.INSTANCE_NAME))
                .list()
                .stream()
                .min(Comparator.comparing((OrgStructurePosition p) ->
                        Objects.equals(p.getIsheadofsubdivision(), 1) ? 0 : 1));
    }

    /**
     * @param picture  the profile photo uploaded by the user, {@code null} when there is none
     * @param employee the HR record the user is linked to, {@code null} when not linked
     */
    public record UserProfile(String username, String displayName, @Nullable String email,
                              @Nullable FileRef picture, @Nullable EmployeeInfo employee) {
    }

    /**
     * @param photoUrl URL of the photo the org chart shows for the employee — the profile photo when the user has
     *                 one, the HR photo otherwise; {@code null} when there is neither
     */
    public record EmployeeInfo(String id, @Nullable String fullName, @Nullable String personnelNumber,
                               @Nullable String email, @Nullable String jobTitle, @Nullable String subdivision,
                               @Nullable String photoUrl) {
    }
}
