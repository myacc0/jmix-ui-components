package com.company.demo.service.orgstructure.photo;

import io.jmix.awsfs.AwsFileStorageProperties;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Finds the employee photos in the S3 file storage — the default under the {@code Production}
 * profile, backed by the MinIO container of {@code docker-compose.yml} — by listing the folder the
 * photos are kept in, {@code employee_photos} by default.
 * <p>
 * Listing only that folder is what keeps the scan proportional to the number of photos rather than
 * to the size of the bucket, which also holds what the rest of the application stores — a user
 * picture, a report output. It also rules out a file elsewhere in the bucket that happens to be
 * named after an employee id being taken for their photo.
 * <p>
 * Set {@code demo.employee-photos.s3-folder} to another folder to move the photos, or to an empty
 * value to search the whole bucket.
 * <p>
 * {@code AwsFileStorage} keeps its {@code S3Client} to itself, so this scanner builds its own from
 * the very same {@code jmix.awsfs.*} properties. The client is created on first use, which means a
 * profile that never reaches for S3 never opens a connection to it.
 */
@Component
public class S3EmployeePhotoScanner implements EmployeePhotoScanner {

    /**
     * Name the S3 storage registers itself under, the value {@code jmix.core.default-file-storage}
     * takes to select it. Spelled out rather than read from {@code AwsFileStorage}, which is
     * internal Jmix API.
     */
    protected static final String STORAGE_NAME = "s3";

    private static final Logger log = LoggerFactory.getLogger(S3EmployeePhotoScanner.class);

    private final AwsFileStorageProperties properties;

    /** Key prefix of the photo folder, always either empty or ending with {@code /}. */
    private final String folderPrefix;

    private volatile S3Client s3Client;

    public S3EmployeePhotoScanner(AwsFileStorageProperties properties,
                                  @Value("${demo.employee-photos.s3-folder:employee_photos}") String folder) {
        this.properties = properties;
        this.folderPrefix = normalizeFolder(folder);
    }

    /**
     * Turns a configured folder name into a key prefix: no leading slash, one trailing slash, so
     * that {@code employee_photos} cannot also match a sibling such as {@code employee_photos_old}.
     * An empty value means the whole bucket.
     */
    private static String normalizeFolder(String folder) {
        String prefix = StringUtils.strip(Objects.toString(folder, "").trim(), "/");
        return prefix.isEmpty() ? "" : prefix + "/";
    }

    @Override
    public String getStorageName() {
        return STORAGE_NAME;
    }

    @Override
    public Map<String, String> scan() {
        PhotoIndexBuilder index = new PhotoIndexBuilder();

        String bucket = properties.getBucket();
        if (StringUtils.isBlank(bucket)) {
            log.warn("No bucket configured in jmix.awsfs.bucket, no employee photo can be found");
            return index.build();
        }

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(folderPrefix)
                    .build();
            for (S3Object object : getS3Client().listObjectsV2Paginator(request).contents()) {
                index.add(object.key(), lastModified(object));
            }
        } catch (SdkException e) {
            log.warn("Cannot list {}/{} for employee photos", bucket, folderPrefix, e);
            return new PhotoIndexBuilder().build();
        }

        return index.build();
    }

    /** A folder placeholder such as {@code employee_photos/} carries no modification time. */
    private long lastModified(S3Object object) {
        return object.lastModified() != null ? object.lastModified().toEpochMilli() : 0L;
    }

    private S3Client getS3Client() {
        S3Client current = s3Client;
        if (current == null) {
            synchronized (this) {
                current = s3Client;
                if (current == null) {
                    current = buildS3Client();
                    s3Client = current;
                }
            }
        }
        return current;
    }

    /** Built exactly the way {@code AwsFileStorage} builds its own client. */
    private S3Client buildS3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(getCredentialsProvider());
        if (StringUtils.isNotBlank(properties.getRegion())) {
            builder.region(Region.of(properties.getRegion()));
        }
        if (StringUtils.isNotBlank(properties.getEndpointUrl())) {
            builder.endpointOverride(URI.create(properties.getEndpointUrl()));
        }
        builder.forcePathStyle(properties.getUsePathStyleBucketAddressing());
        return builder.build();
    }

    private AwsCredentialsProvider getCredentialsProvider() {
        // absent under a profile that does not configure S3; the SDK then looks the credentials
        // up the standard way (environment, profile file, instance role)
        if (StringUtils.isNotBlank(properties.getAccessKey())
                && StringUtils.isNotBlank(properties.getSecretAccessKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretAccessKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    @PreDestroy
    public void closeS3Client() {
        S3Client current = s3Client;
        if (current != null) {
            current.close();
            s3Client = null;
        }
    }
}
