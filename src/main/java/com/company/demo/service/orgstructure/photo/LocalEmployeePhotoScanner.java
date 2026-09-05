package com.company.demo.service.orgstructure.photo;

import io.jmix.core.CoreProperties;
import io.jmix.localfs.LocalFileStorageProperties;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds the employee photos in the local file storage — the default under the {@code Dev}
 * profile — by walking its storage roots.
 */
@Component
public class LocalEmployeePhotoScanner implements EmployeePhotoScanner {

    /**
     * Name the local storage registers itself under, the value {@code jmix.core.default-file-storage}
     * takes to select it. Spelled out rather than read from {@code LocalFileStorage}, which is
     * internal Jmix API.
     */
    protected static final String STORAGE_NAME = "fs";

    protected static final String DEFAULT_STORAGE_DIR_NAME = "filestorage";

    private static final Logger log = LoggerFactory.getLogger(LocalEmployeePhotoScanner.class);

    private final LocalFileStorageProperties localFileStorageProperties;
    private final CoreProperties coreProperties;

    public LocalEmployeePhotoScanner(LocalFileStorageProperties localFileStorageProperties,
                                     CoreProperties coreProperties) {
        this.localFileStorageProperties = localFileStorageProperties;
        this.coreProperties = coreProperties;
    }

    @Override
    public String getStorageName() {
        return STORAGE_NAME;
    }

    @Override
    public Map<String, String> scan() {
        PhotoIndexBuilder index = new PhotoIndexBuilder();

        for (Path root : getStorageRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> indexFile(root, file, index));
            } catch (IOException | UncheckedIOException e) {
                log.warn("Cannot scan file storage directory {} for employee photos", root, e);
            }
        }

        return index.build();
    }

    private void indexFile(Path root, Path file, PhotoIndexBuilder index) {
        String storagePath = toStoragePath(root.relativize(file));
        if (!isAddressableByStorage(storagePath)) {
            log.debug("Skipping {}: not in the yyyy/MM/dd layout the file storage can address", file);
            return;
        }
        index.add(storagePath, lastModified(file));
    }

    /**
     * Resolves the local file storage roots the same way {@code io.jmix.localfs.LocalFileStorage}
     * does: the comma-separated {@code jmix.localfs.storage-dir} property, or
     * {@code jmix.core.work-dir/filestorage} when it is not set — which is the case under a
     * profile that configures S3 instead.
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
}
