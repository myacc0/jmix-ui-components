package com.company.demo.orgstructure;

import com.company.demo.dto.orgstructure.OrgChartNode;
import com.company.demo.entity.orgstructure.Department;
import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.entity.orgstructure.JobTitle;
import com.company.demo.entity.orgstructure.Position;
import com.company.demo.enums.orgstructure.PositionStatus;
import com.company.demo.service.orgstructure.EmployeePhotoService;
import com.company.demo.service.orgstructure.OrgStructureService;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that {@link EmployeePhotoService} serves the photos from the S3 bucket when the
 * {@code Production} profile makes {@code s3} the default file storage — the same behaviour the
 * sibling {@code EmployeePhotoServiceTests} covers for the local storage of the {@code Dev} profile.
 * <p>
 * The fixture puts its objects into the bucket directly, under the {@code employee_photos} folder
 * the photos live in by business rule — a key of two segments, which the local storage could not
 * address at all.
 * <p>
 * Skipped, not failed, when MinIO is not running — {@code docker-compose up -d minio} enables it.
 */
@SpringBootTest
@ActiveProfiles("Production")
@ExtendWith(AuthenticatedAsAdmin.class)
class EmployeePhotoS3StorageTests {

    private static final byte[] PHOTO_CONTENT = "not-a-real-jpeg".getBytes();
    private static final String PHOTO_FOLDER = "employee_photos/";
    private static final int MINIO_PROBE_TIMEOUT_MS = 1_000;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EmployeePhotoService employeePhotoService;
    @Autowired
    private OrgStructureService orgStructureService;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    @Value("${jmix.awsfs.endpoint-url}")
    private String endpointUrl;
    @Value("${jmix.awsfs.region}")
    private String region;
    @Value("${jmix.awsfs.bucket}")
    private String bucket;
    @Value("${jmix.awsfs.access-key}")
    private String accessKey;
    @Value("${jmix.awsfs.secret-access-key}")
    private String secretAccessKey;

    private S3Client s3Client;
    private final List<String> createdKeys = new ArrayList<>();
    private final List<Object> cleanup = new ArrayList<>();

    @BeforeEach
    void setUp() {
        assumeTrue(isMinioReachable(), "MinIO is not running at " + endpointUrl);
        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretAccessKey)))
                .region(Region.of(region))
                .endpointOverride(URI.create(endpointUrl))
                .forcePathStyle(true)
                .build();
    }

    @Test
    void theProductionProfileMakesS3TheDefaultStorage() {
        assertEquals("s3", fileStorageLocator.getDefault().getStorageName());
    }

    @Test
    void findsPhotoNamedAfterTheEmployeeIdInTheBucket() {
        Employee employee = createEmployee();
        String key = putPhoto(PHOTO_FOLDER + employee.getId() + ".jpg");

        Optional<FileRef> fileRef = employeePhotoService.findPhotoRef(employee.getId());
        assertTrue(fileRef.isPresent(), "photo named after the employee id must be found");
        assertEquals(key, fileRef.get().getPath());
        assertEquals("s3", fileRef.get().getStorageName());

        // the reference must be usable by the file storage itself, not only by the index
        FileStorage fileStorage = fileStorageLocator.getDefault();
        assertTrue(fileStorage.fileExists(fileRef.get()));
    }

    @Test
    void readsPhotoContentAndContentTypeFromTheBucket() {
        Employee employee = createEmployee();
        putPhoto(PHOTO_FOLDER + employee.getId() + ".jpg");

        EmployeePhotoService.Photo photo = employeePhotoService.loadPhoto(employee.getId()).orElseThrow();

        assertArrayEquals(PHOTO_CONTENT, photo.content());
        assertEquals("image/jpeg", photo.contentType());
    }

    /**
     * The photos live in one folder by business rule, so the scan is bound to it: an image
     * elsewhere in the bucket — a user picture, a report output — is none of its business even
     * when its name happens to be an employee id.
     */
    @Test
    void ignoresAnImageOutsideThePhotoFolder() {
        Employee employee = createEmployee();
        putPhoto("1970/01/01/" + employee.getId() + ".png");

        assertTrue(employeePhotoService.findPhotoRef(employee.getId()).isEmpty(),
                "only the employee_photos folder is searched");
    }

    /** The prefix ends with a slash, so a folder that merely starts with the same name is not it. */
    @Test
    void ignoresAnImageInASiblingFolderWithTheSameNamePrefix() {
        Employee employee = createEmployee();
        putPhoto("employee_photos_old/" + employee.getId() + ".jpg");

        assertTrue(employeePhotoService.findPhotoRef(employee.getId()).isEmpty(),
                "employee_photos_old must not be taken for employee_photos");
    }

    @Test
    void buildsPhotoUrlOnlyForAnEmployeeThatHasAPhoto() {
        Employee withPhoto = createEmployee();
        Employee withoutPhoto = createEmployee();
        putPhoto(PHOTO_FOLDER + withPhoto.getId() + ".jpg");

        assertEquals("/employee-photos/" + withPhoto.getId(),
                employeePhotoService.getPhotoUrl(withPhoto.getId()));
        assertNull(employeePhotoService.getPhotoUrl(withoutPhoto.getId()));
    }

    @Test
    void ignoresAnObjectThatIsNotAnImage() {
        Employee employee = createEmployee();
        putPhoto(PHOTO_FOLDER + employee.getId() + ".txt");

        assertTrue(employeePhotoService.findPhotoRef(employee.getId()).isEmpty());
        assertTrue(employeePhotoService.loadPhoto(employee.getId()).isEmpty());
    }

    @Test
    void returnsNothingForAnEmployeeWithoutAPhoto() {
        employeePhotoService.invalidateIndex();

        assertTrue(employeePhotoService.findPhotoRef(UUID.randomUUID()).isEmpty());
        assertTrue(employeePhotoService.findPhotoRef(null).isEmpty());
    }

    /**
     * The whole chain the org chart depends on: {@code OrgStructureService} asks
     * {@code EmployeePhotoService} for a URL, which finds the photo in the bucket.
     */
    @Test
    void putsTheS3PhotoUrlOfTheEmployeeOnTheOrgChartNode() {
        String suffix = UUID.randomUUID().toString();

        Department root = createDepartment("S3 Root " + suffix);
        JobTitle title = createJobTitle("S3 Director " + suffix);

        Employee withPhoto = createEmployee();
        Employee withoutPhoto = createEmployee();
        Position head = createPosition(root, title, withPhoto, 10, 1);
        Position staff = createPosition(root, title, withoutPhoto, 6, 0);

        putPhoto(PHOTO_FOLDER + withPhoto.getId() + ".jpg");

        Map<String, OrgChartNode> nodes = orgStructureService.getOrgChartNodes(root.getId()).stream()
                .collect(Collectors.toMap(OrgChartNode::getId, Function.identity()));

        assertEquals("/employee-photos/" + withPhoto.getId(), nodes.get("pos-" + head.getId()).getImage());
        // no photo in the bucket: orgchart.js falls back to the initials placeholder on a null image
        assertNull(nodes.get("pos-" + staff.getId()).getImage());
    }

    private Department createDepartment(String name) {
        Department department = dataManager.create(Department.class);
        department.setName(name);
        department.setOrdNo(1);
        Department saved = dataManager.save(department);
        cleanup.add(saved);
        return saved;
    }

    private JobTitle createJobTitle(String name) {
        JobTitle jobTitle = dataManager.create(JobTitle.class);
        jobTitle.setName(name);
        JobTitle saved = dataManager.save(jobTitle);
        cleanup.add(saved);
        return saved;
    }

    private Position createPosition(Department department, JobTitle jobTitle, Employee employee,
                                    Integer lvl, Integer ishead) {
        Position position = dataManager.create(Position.class);
        position.setDepartment(department);
        position.setJobTitle(jobTitle);
        position.setEmployee(employee);
        position.setLvl(lvl);
        position.setIshead(ishead);
        position.setStatus(PositionStatus.FILLED);
        Position saved = dataManager.save(position);
        cleanup.add(saved);
        return saved;
    }

    private Employee createEmployee() {
        Employee employee = dataManager.create(Employee.class);
        employee.setFullName("S3 Photo Test " + UUID.randomUUID());
        Employee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }

    /**
     * Puts an object into the bucket and drops the cached index, so that the new photo is picked
     * up right away instead of at the end of the index TTL.
     */
    private String putPhoto(String key) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(PHOTO_CONTENT));
        createdKeys.add(key);
        employeePhotoService.invalidateIndex();
        return key;
    }

    private boolean isMinioReachable() {
        URI uri = URI.create(endpointUrl);
        int port = uri.getPort() != -1 ? uri.getPort() : 80;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), MINIO_PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @AfterEach
    void tearDown() {
        if (s3Client != null) {
            for (String key : createdKeys) {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            }
            s3Client.close();
            s3Client = null;
        }
        createdKeys.clear();

        // reverse creation order: positions before the departments/employees they reference
        List<Object> reversed = new ArrayList<>(cleanup);
        Collections.reverse(reversed);
        reversed.forEach(dataManager::remove);
        cleanup.clear();

        // the index must not keep pointing at the objects the test has just removed
        employeePhotoService.invalidateIndex();
    }
}
