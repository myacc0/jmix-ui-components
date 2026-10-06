package uz.kapitalbank.umida.service.orgstructure;

import io.jmix.core.FetchPlan;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageException;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.event.EntityChangedEvent;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.orgstructure.photo.EmployeePhotoScanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Resolves the photo of an employee in the Jmix file storage.
 * <p>
 * The photos are not referenced by a {@code FileRef} attribute of {@code OrgStructureEmployee} — they are
 * plain files whose name is the employee id, for example
 * {@code filestorage/2026/07/28/01a03df1-d6a1-7631-aa18-38f9bd589100.jpg} in the local storage or
 * {@code employee_photos/01a03df1-d6a1-7631-aa18-38f9bd589100.jpg} in the S3 bucket. Since the
 * extension and, in the local storage, the directory of a given employee id are unknown up front,
 * this service keeps a short-lived index of
 * {@code employee id -> path inside the storage}, enumerated by the {@link EmployeePhotoScanner}
 * of the storage in use.
 * <p>
 * The service follows whatever storage is the default ({@code jmix.core.default-file-storage}), so
 * it serves photos from the local directory under the {@code Dev} profile and from the S3 bucket
 * under the {@code Production} profile with no change to the callers. A storage that no scanner
 * covers leaves the index empty, and every employee is then simply reported as having no photo.
 * <p>
 * The index is rebuilt at most once per {@link #INDEX_TTL_MS}, which means a photo put into the
 * storage outside the application becomes visible within that interval without a restart.
 * <p>
 * The profile photo a user uploads ({@code User.profilePhoto}) takes precedence over the employee photo of the
 * employee the user is linked to ({@code User.employee}). Those are looked up in a second short-lived index,
 * {@code employee id -> User.profilePhoto}, dropped whenever a user's profilePhoto or employee link changes. The URL of a
 * profile photo carries a version of the file, so a browser that cached the previous profilePhoto fetches the new one
 * right away.
 */
@Service
public class EmployeePhotoService {

    /**
     * Path the photos are served under, see {@code EmployeePhotoController}. Also used to build
     * the {@code image} URL of an org chart node.
     */
    public static final String PHOTO_URL_PREFIX = "/employee-photos/";

    protected static final long INDEX_TTL_MS = 60_000L;

    private static final Logger log = LoggerFactory.getLogger(EmployeePhotoService.class);

    private final FileStorageLocator fileStorageLocator;
    private final List<EmployeePhotoScanner> photoScanners;
    private final UnconstrainedDataManager dataManager;
    private final String contextPath;

    private volatile PhotoIndex photoIndex;
    private volatile UserProfilePhotoIndex userProfilePhotoIndex;

    public EmployeePhotoService(FileStorageLocator fileStorageLocator,
                                List<EmployeePhotoScanner> photoScanners,
                                UnconstrainedDataManager dataManager,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.fileStorageLocator = fileStorageLocator;
        this.photoScanners = photoScanners;
        this.dataManager = dataManager;
        this.contextPath = normalizeContextPath(contextPath);
    }

    /** A context path is prepended to the photo URL as is, so it must not end with a slash. */
    private static String normalizeContextPath(String contextPath) {
        String path = Objects.toString(contextPath, "").trim();
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Returns the URL the photo of the employee is served under, or {@code null} when neither the
     * user linked to the employee has a profile photo nor the employee has a photo in the file
     * storage. A {@code null} lets the org chart fall back to the initials placeholder.
     */
    public String getPhotoUrl(String employeeId) {
        Optional<FileRef> userProfilePhoto = findUserProfilePhotoRef(employeeId);
        if (userProfilePhoto.isPresent()) {
            // the version changes with the file, so a cached previous profilePhoto is not reused
            return contextPath + PHOTO_URL_PREFIX + employeeId
                    + "?v=" + Integer.toHexString(userProfilePhoto.get().toString().hashCode());
        }
        return findPhotoRef(employeeId)
                .map(fileRef -> contextPath + PHOTO_URL_PREFIX + employeeId)
                .orElse(null);
    }

    /**
     * Returns the profile photo ({@code User.profilePhoto}) of the user linked to the employee, or an
     * empty optional when no linked user has one.
     */
    public Optional<FileRef> findUserProfilePhotoRef(String employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getUserProfilePhotoIndex().get(employeeId.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns the file storage reference of the employee photo, or an empty optional when there
     * is no file named after the employee id in the storage.
     */
    public Optional<FileRef> findPhotoRef(String employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }

        String storagePath = getIndex().get(employeeId.toString().toLowerCase(Locale.ROOT));
        if (storagePath == null) {
            return Optional.empty();
        }

        FileRef fileRef = new FileRef(getFileStorage().getStorageName(),
                storagePath, FilenameUtils.getName(storagePath));

        // the index may be stale — the file could have been removed since the last scan
        return getFileStorage().fileExists(fileRef) ? Optional.of(fileRef) : Optional.empty();
    }

    /**
     * Reads the photo of the employee: the profile photo of the linked user if there is one,
     * otherwise the employee photo. Returns an empty optional when there is neither or the file
     * cannot be read.
     */
    public Optional<Photo> loadPhoto(String employeeId) {
        Optional<Photo> userProfilePhoto = findUserProfilePhotoRef(employeeId)
                .flatMap(fileRef -> readPhoto(employeeId, fileRef));
        if (userProfilePhoto.isPresent()) {
            return userProfilePhoto;
        }
        // a profile photo that cannot be read falls back to the employee photo
        return findPhotoRef(employeeId).flatMap(fileRef -> readPhoto(employeeId, fileRef));
    }

    private Optional<Photo> readPhoto(String employeeId, FileRef reference) {
        try (InputStream inputStream = fileStorageLocator.getByName(reference.getStorageName())
                .openStream(reference)) {
            return Optional.of(new Photo(reference.getFileName(), reference.getContentType(),
                    inputStream.readAllBytes()));
        } catch (IOException | FileStorageException | IllegalArgumentException | IllegalStateException e) {
            // IllegalArgument/IllegalState: the storage the reference names is not registered (any more)
            log.warn("Cannot read the photo of employee {} from {}", employeeId, reference, e);
            return Optional.empty();
        }
    }

    /**
     * Drops the cached index, so that the next lookup rescans the storage. Call it after a
     * photo file has been added or removed to make the change visible immediately instead of
     * waiting for {@link #INDEX_TTL_MS}.
     */
    public void invalidateIndex() {
        photoIndex = null;
        userProfilePhotoIndex = null;
    }

    /**
     * Drops the profile photo index once a change of a user's profilePhoto or employee link is
     * committed, so that the org chart shows the new photo right away.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onUserChanged(EntityChangedEvent<User> event) {
        if (event.getType() != EntityChangedEvent.Type.UPDATED
                || event.getChanges().isChanged("profilePhoto")
                || event.getChanges().isChanged("employee")) {
            userProfilePhotoIndex = null;
        }
    }

    private Map<String, FileRef> getUserProfilePhotoIndex() {
        UserProfilePhotoIndex current = userProfilePhotoIndex;
        if (current != null && System.currentTimeMillis() - current.builtAt() < INDEX_TTL_MS) {
            return current.profilePhotoByEmployeeId();
        }

        UserProfilePhotoIndex rebuilt = new UserProfilePhotoIndex(loadUserProfilePhoto(), System.currentTimeMillis());
        userProfilePhotoIndex = rebuilt;
        return rebuilt.profilePhotoByEmployeeId();
    }

    private Map<String, FileRef> loadUserProfilePhoto() {
        List<User> users = dataManager.load(User.class)
                .query("select u from umida_User u where u.employee is not null and u.profilePhoto is not null")
                .fetchPlan(fp -> fp.add("profilePhoto")
                        .add("employee", FetchPlan.INSTANCE_NAME))
                .list();

        Map<String, FileRef> profilePhotoByEmployeeId = new HashMap<>();
        for (User user : users) {
            profilePhotoByEmployeeId.put(user.getEmployee().getId().toLowerCase(Locale.ROOT), user.getProfilePhoto());
        }
        return profilePhotoByEmployeeId;
    }

    private FileStorage getFileStorage() {
        return fileStorageLocator.getDefault();
    }

    private Map<String, String> getIndex() {
        String storageName = getFileStorage().getStorageName();

        PhotoIndex current = photoIndex;
        if (current != null
                && current.storageName().equals(storageName)
                && System.currentTimeMillis() - current.builtAt() < INDEX_TTL_MS) {
            return current.pathsByEmployeeId();
        }

        PhotoIndex rebuilt = new PhotoIndex(buildIndex(storageName), storageName,
                System.currentTimeMillis());
        photoIndex = rebuilt;
        return rebuilt.pathsByEmployeeId();
    }

    /**
     * Enumerates the photos with the scanner of the storage in use. A storage no scanner covers is
     * not an error: the org chart then renders the initials placeholder for every employee.
     */
    private Map<String, String> buildIndex(String storageName) {
        return findScanner(storageName)
                .map(EmployeePhotoScanner::scan)
                .orElseGet(() -> {
                    log.warn("No employee photo scanner for the file storage '{}', "
                            + "employees are reported as having no photo", storageName);
                    return Map.of();
                });
    }

    private Optional<EmployeePhotoScanner> findScanner(String storageName) {
        return photoScanners.stream()
                .filter(scanner -> scanner.getStorageName().equals(storageName))
                .findFirst();
    }

    /** Photo content together with what is needed to serve it. */
    public record Photo(String fileName, String contentType, byte[] content) {
    }

    private record UserProfilePhotoIndex(Map<String, FileRef> profilePhotoByEmployeeId, long builtAt) {
    }

    /** The index is tied to the storage it was built from, so a storage switch cannot reuse it. */
    private record PhotoIndex(Map<String, String> pathsByEmployeeId, String storageName, long builtAt) {
    }
}
