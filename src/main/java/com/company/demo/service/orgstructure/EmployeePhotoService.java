package com.company.demo.service.orgstructure;

import com.company.demo.service.orgstructure.photo.EmployeePhotoScanner;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageException;
import io.jmix.core.FileStorageLocator;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the photo of an employee in the Jmix file storage.
 * <p>
 * The photos are not referenced by a {@code FileRef} attribute of {@code Employee} — they are
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
    private final String contextPath;

    private volatile PhotoIndex photoIndex;

    public EmployeePhotoService(FileStorageLocator fileStorageLocator,
                                List<EmployeePhotoScanner> photoScanners,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.fileStorageLocator = fileStorageLocator;
        this.photoScanners = photoScanners;
        this.contextPath = normalizeContextPath(contextPath);
    }

    /** A context path is prepended to the photo URL as is, so it must not end with a slash. */
    private static String normalizeContextPath(String contextPath) {
        String path = Objects.toString(contextPath, "").trim();
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Returns the URL the photo of the employee is served under, or {@code null} when the
     * employee has no photo in the file storage. A {@code null} lets the org chart fall back to
     * the initials placeholder.
     */
    public String getPhotoUrl(UUID employeeId) {
        return findPhotoRef(employeeId)
                .map(fileRef -> contextPath + PHOTO_URL_PREFIX + employeeId)
                .orElse(null);
    }

    /**
     * Returns the file storage reference of the employee photo, or an empty optional when there
     * is no file named after the employee id in the storage.
     */
    public Optional<FileRef> findPhotoRef(UUID employeeId) {
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
     * Reads the photo of the employee. Returns an empty optional when the employee has no photo
     * or the file cannot be read.
     */
    public Optional<Photo> loadPhoto(UUID employeeId) {
        Optional<FileRef> fileRef = findPhotoRef(employeeId);
        if (fileRef.isEmpty()) {
            return Optional.empty();
        }

        FileRef reference = fileRef.get();
        try (InputStream inputStream = getFileStorage().openStream(reference)) {
            return Optional.of(new Photo(reference.getFileName(), reference.getContentType(),
                    inputStream.readAllBytes()));
        } catch (IOException | FileStorageException e) {
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

    /** The index is tied to the storage it was built from, so a storage switch cannot reuse it. */
    private record PhotoIndex(Map<String, String> pathsByEmployeeId, String storageName, long builtAt) {
    }
}
