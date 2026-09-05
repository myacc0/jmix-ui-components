package com.company.demo.service.orgstructure.photo;

import java.util.Map;

/**
 * Finds the employee photos held by one kind of Jmix file storage.
 * <p>
 * A photo is not referenced by an attribute of {@code Employee} — it is a file named after the
 * employee id, so the only way to find it is to enumerate what the storage holds. Every storage
 * enumerates differently (a directory walk for the local one, a bucket listing for S3), hence one
 * implementation per storage; {@code EmployeePhotoService} picks the one whose
 * {@link #getStorageName()} matches the storage that is currently the default, which is what makes
 * the photos work under the {@code Dev} profile ({@code fs}) and the {@code Production} profile
 * ({@code s3}) alike.
 */
public interface EmployeePhotoScanner {

    /**
     * Name of the Jmix file storage this scanner reads, as returned by
     * {@code FileStorage.getStorageName()} — {@code fs} for the local storage, {@code s3} for S3.
     */
    String getStorageName();

    /**
     * Enumerates the photos in the storage as {@code file name without extension -> path inside
     * the storage}. The name is lower-cased, so the caller can look an employee id up directly.
     * <p>
     * A storage that cannot be read yields an empty map rather than an exception: a missing photo
     * degrades to the initials placeholder in the org chart, it never breaks the chart.
     */
    Map<String, String> scan();
}
