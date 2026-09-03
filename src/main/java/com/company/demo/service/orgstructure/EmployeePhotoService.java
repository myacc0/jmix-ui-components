package com.company.demo.service.orgstructure;

import io.jmix.core.CoreProperties;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageException;
import io.jmix.core.FileStorageLocator;
import io.jmix.localfs.LocalFileStorageProperties;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Resolves the photo of an employee in the Jmix file storage.
 * <p>
 * The photos are not referenced by a {@code FileRef} attribute of {@code Employee} — they are
 * plain files whose name is the employee id, for example
 * {@code filestorage/2026/07/28/01a03df1-d6a1-7631-aa18-38f9bd589100.jpg}. Since the storage
 * layout puts a file into a {@code yyyy/MM/dd} directory chosen at upload time, the directory
 * of a given employee id is unknown up front, so this service keeps a short-lived index of
 * {@code employee id -> path inside the storage} built by scanning the storage roots.
 * <p>
 * The index is rebuilt at most once per {@link #INDEX_TTL_MS}, which means a photo uploaded
 * into the storage directory outside the application becomes visible within that interval
 * without a restart.
 * <p>
 * Assumes the default file storage is the local one ({@code jmix.localfs.storage-dir}); with a
 * different default storage the index stays empty and every employee is simply reported as
 * having no photo.
 */
@Service
public class EmployeePhotoService {

    /**
     * Path the photos are served under, see {@code EmployeePhotoController}. Also used to build
     * the {@code image} URL of an org chart node.
     */
    public static final String PHOTO_URL_PREFIX = "/employee-photos/";

    protected static final long INDEX_TTL_MS = 60_000L;
    protected static final String DEFAULT_STORAGE_DIR_NAME = "filestorage";
    protected static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

    private static final Logger log = LoggerFactory.getLogger(EmployeePhotoService.class);

    private final FileStorageLocator fileStorageLocator;
    private final LocalFileStorageProperties localFileStorageProperties;
    private final CoreProperties coreProperties;
    private final String contextPath;

    private volatile PhotoIndex photoIndex;

    public EmployeePhotoService(FileStorageLocator fileStorageLocator,
                                LocalFileStorageProperties localFileStorageProperties,
                                CoreProperties coreProperties,
                                @Value("${server.servlet.context-path:}") String contextPath) {
        this.fileStorageLocator = fileStorageLocator;
        this.localFileStorageProperties = localFileStorageProperties;
        this.coreProperties = coreProperties;
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

        String relativePath = getIndex().get(employeeId.toString().toLowerCase(Locale.ROOT));
        if (relativePath == null) {
            return Optional.empty();
        }

        FileRef fileRef = new FileRef(getFileStorage().getStorageName(),
                relativePath, FilenameUtils.getName(relativePath));

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
        PhotoIndex current = photoIndex;
        if (current != null && System.currentTimeMillis() - current.builtAt() < INDEX_TTL_MS) {
            return current.pathsByEmployeeId();
        }

        PhotoIndex rebuilt = new PhotoIndex(buildIndex(), System.currentTimeMillis());
        photoIndex = rebuilt;
        return rebuilt.pathsByEmployeeId();
    }

    /**
     * Scans the storage roots and maps the name of every image file, without its extension, to
     * the path of that file inside the storage. When the same name occurs more than once, the
     * most recently modified file wins.
     */
    private Map<String, String> buildIndex() {
        Map<String, PhotoFile> filesByName = new HashMap<>();

        for (Path root : getStorageRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> indexFile(root, file, filesByName));
            } catch (IOException | UncheckedIOException e) {
                log.warn("Cannot scan file storage directory {} for employee photos", root, e);
            }
        }

        Map<String, String> pathsByName = new HashMap<>();
        filesByName.forEach((name, photoFile) -> pathsByName.put(name, photoFile.relativePath()));
        return pathsByName;
    }

    private void indexFile(Path root, Path file, Map<String, PhotoFile> filesByName) {
        String fileName = file.getFileName().toString();
        String extension = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return;
        }

        // trimmed: a file name may carry stray whitespace around the id it was named after
        String name = FilenameUtils.getBaseName(fileName).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return;
        }

        String storagePath = toStoragePath(root.relativize(file));
        if (!isAddressableByStorage(storagePath)) {
            log.debug("Skipping {}: not in the yyyy/MM/dd layout the file storage can address", file);
            return;
        }

        PhotoFile candidate = new PhotoFile(storagePath, lastModified(file));
        filesByName.merge(name, candidate,
                (existing, added) -> added.lastModified() > existing.lastModified() ? added : existing);
    }

    /**
     * Resolves the local file storage roots the same way {@code io.jmix.localfs.LocalFileStorage}
     * does: the comma-separated {@code jmix.localfs.storage-dir} property, or
     * {@code jmix.core.work-dir/filestorage} when it is not set.
     */
    private List<Path> getStorageRoots() {
        String storageDir = localFileStorageProperties.getStorageDir();
        if (StringUtils.isBlank(storageDir)) {
            return List.of(Paths.get(coreProperties.getWorkDir(), DEFAULT_STORAGE_DIR_NAME));
        }

        List<Path> roots = new ArrayList<>();
        for (String dir : storageDir.split(",")) {
            dir = dir.trim();
            if (!dir.isEmpty()) {
                Path root = Paths.get(dir);
                if (!roots.contains(root)) {
                    roots.add(root);
                }
            }
        }
        return roots;
    }

    /**
     * {@code LocalFileStorage} only accepts a path of at least four segments — the
     * {@code yyyy/MM/dd} directory plus the file name — and throws on anything shorter, so a
     * file lying elsewhere under the root is not indexed at all.
     */
    private boolean isAddressableByStorage(String storagePath) {
        return storagePath.split("/").length >= 4;
    }

    /** A path inside the storage always uses {@code /}, whatever the file system separator is. */
    private String toStoragePath(Path relativePath) {
        return FilenameUtils.separatorsToUnix(relativePath.toString());
    }

    private long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            log.debug("Cannot read the modification time of {}", file, e);
            return 0L;
        }
    }

    /** Photo content together with what is needed to serve it. */
    public record Photo(String fileName, String contentType, byte[] content) {
    }

    private record PhotoFile(String relativePath, long lastModified) {
    }

    private record PhotoIndex(Map<String, String> pathsByEmployeeId, long builtAt) {
    }
}
