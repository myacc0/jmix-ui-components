package uz.kapitalbank.umida.orgstructure;

import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.entity.orgstructure.OrgStructureEmployee;
import uz.kapitalbank.umida.service.orgstructure.EmployeePhotoService;
import uz.kapitalbank.umida.test_support.AuthenticatedAsAdmin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The profile photo of the user linked to an employee wins over the employee photo, in the URL the org chart gets
 * and in the content served under it, and the change is visible right after the save.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class UserProfilePhotoTests {

    private static final byte[] HR_PHOTO = "hr-photo".getBytes();
    private static final byte[] PROFILE_PHOTO = "profile-photo".getBytes();
    private static final String FIXTURE_DATE_DIR = "1970/01/02";

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EmployeePhotoService employeePhotoService;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    @Value("${jmix.localfs.storage-dir}")
    private String storageDir;

    private final List<Object> cleanup = new ArrayList<>();
    private final List<FileRef> storedFiles = new ArrayList<>();
    private final List<Path> createdFiles = new ArrayList<>();

    @Test
    void profilePhotoReplacesTheEmployeePhotoUntilRemoved() throws IOException {
        OrgStructureEmployee employee = createEmployee();
        writeHrPhoto(employee.getId() + ".jpg");
        User user = createUser(employee);

        assertThat(employeePhotoService.getPhotoUrl(employee.getId()))
                .isEqualTo("/employee-photos/" + employee.getId());

        FileRef picture = storeFile("me.png", PROFILE_PHOTO);
        user.setPicture(picture);
        user = dataManager.save(user);

        String profileUrl = employeePhotoService.getPhotoUrl(employee.getId());
        assertThat(profileUrl).startsWith("/employee-photos/" + employee.getId() + "?v=");
        assertThat(employeePhotoService.loadPhoto(employee.getId()).orElseThrow().content())
                .isEqualTo(PROFILE_PHOTO);

        // a new picture gets a new URL, so the browser does not keep showing the cached one
        user.setPicture(storeFile("me-again.png", PROFILE_PHOTO));
        user = dataManager.save(user);
        assertThat(employeePhotoService.getPhotoUrl(employee.getId())).isNotEqualTo(profileUrl);

        user.setPicture(null);
        dataManager.save(user);

        assertThat(employeePhotoService.getPhotoUrl(employee.getId()))
                .isEqualTo("/employee-photos/" + employee.getId());
        assertThat(employeePhotoService.loadPhoto(employee.getId()).orElseThrow().content())
                .isEqualTo(HR_PHOTO);
    }

    @Test
    void profilePhotoIsShownForEmployeeWithoutHrPhoto() {
        OrgStructureEmployee employee = createEmployee();
        User user = createUser(employee);
        assertThat(employeePhotoService.getPhotoUrl(employee.getId())).isNull();

        user.setPicture(storeFile("me.png", PROFILE_PHOTO));
        dataManager.save(user);

        assertThat(employeePhotoService.getPhotoUrl(employee.getId())).isNotNull();
        assertThat(employeePhotoService.loadPhoto(employee.getId()).orElseThrow().content())
                .isEqualTo(PROFILE_PHOTO);
    }

    private OrgStructureEmployee createEmployee() {
        OrgStructureEmployee employee = dataManager.create(OrgStructureEmployee.class);
        employee.setId(UUID.randomUUID().toString());
        employee.setFullName("Profile Photo Test " + UUID.randomUUID());
        OrgStructureEmployee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }

    private User createUser(OrgStructureEmployee employee) {
        User user = dataManager.create(User.class);
        user.setUsername("profile-photo-test-" + UUID.randomUUID());
        user.setEmployee(employee);
        User saved = dataManager.save(user);
        // removed before the employee it references
        cleanup.add(0, saved);
        return saved;
    }

    private FileRef storeFile(String fileName, byte[] content) {
        FileStorage fileStorage = fileStorageLocator.getDefault();
        FileRef fileRef = fileStorage.saveStream(fileName, new ByteArrayInputStream(content));
        storedFiles.add(fileRef);
        return fileRef;
    }

    /** Same layout as {@code OrgStructureEmployeePhotoServiceTests}: a date no upload can produce. */
    private void writeHrPhoto(String fileName) throws IOException {
        Path directory = Paths.get(storageDir.split(",")[0].trim(), FIXTURE_DATE_DIR);
        Files.createDirectories(directory);
        Path file = directory.resolve(fileName);
        Files.write(file, HR_PHOTO);
        createdFiles.add(file);
        employeePhotoService.invalidateIndex();
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        try {
            Files.deleteIfExists(directory);
        } catch (DirectoryNotEmptyException e) {
            // another fixture directory is still there
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path file : createdFiles) {
            Files.deleteIfExists(file);
            deleteIfEmpty(file.getParent());
            deleteIfEmpty(file.getParent().getParent());
            deleteIfEmpty(file.getParent().getParent().getParent());
        }
        createdFiles.clear();

        // by id: the fixture instances are stale once a test saved them again
        cleanup.forEach(entity -> dataManager.remove(Id.of(entity)));
        cleanup.clear();

        FileStorage fileStorage = fileStorageLocator.getDefault();
        storedFiles.forEach(fileStorage::removeFile);
        storedFiles.clear();

        employeePhotoService.invalidateIndex();
    }
}
