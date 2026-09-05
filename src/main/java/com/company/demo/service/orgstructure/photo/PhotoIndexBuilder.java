package com.company.demo.service.orgstructure.photo;

import org.apache.commons.io.FilenameUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Collects the photo candidates a scanner finds and turns them into the
 * {@code file name -> storage path} index. Keeps the rules that do not depend on the storage:
 * only image files count, the name is the file name without extension, and when the same name
 * occurs more than once the most recently modified file wins.
 */
public class PhotoIndexBuilder {

    protected static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

    private final Map<String, PhotoFile> filesByName = new HashMap<>();

    /**
     * Offers a file of the storage to the index. Anything that is not an image or has no name
     * beside its extension is ignored.
     *
     * @param storagePath  path of the file inside the storage, always with {@code /} separators
     * @param lastModified modification time, used to break a tie between two files of one name
     */
    public void add(String storagePath, long lastModified) {
        String fileName = FilenameUtils.getName(storagePath);
        String extension = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            return;
        }

        // trimmed: a file name may carry stray whitespace around the id it was named after
        String name = FilenameUtils.getBaseName(fileName).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return;
        }

        filesByName.merge(name, new PhotoFile(storagePath, lastModified),
                (existing, added) -> added.lastModified() > existing.lastModified() ? added : existing);
    }

    public Map<String, String> build() {
        Map<String, String> pathsByName = new HashMap<>();
        filesByName.forEach((name, photoFile) -> pathsByName.put(name, photoFile.storagePath()));
        return pathsByName;
    }

    private record PhotoFile(String storagePath, long lastModified) {
    }
}
