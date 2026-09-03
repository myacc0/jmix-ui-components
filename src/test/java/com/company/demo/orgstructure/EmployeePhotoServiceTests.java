package com.company.demo.orgstructure;

import com.company.demo.entity.orgstructure.Employee;
import com.company.demo.service.orgstructure.EmployeePhotoService;
import com.company.demo.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link EmployeePhotoService} finds a photo stored in the local file storage
 * under the employee id, whatever date directory it happens to sit in.
 * <p>
 * The fixture writes its own file into a directory of the storage root that the test creates
 * and deletes afterwards, so it neither depends on nor touches the photos a developer already
 * put into {@code filestorage}.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
class EmployeePhotoServiceTests {

    private static final byte[] PHOTO_CONTENT = "not-a-real-jpeg".getBytes();
    private static final String FIXTURE_DATE_DIR = "1970/01/01";

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EmployeePhotoService employeePhotoService;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    @Value("${jmix.localfs.storage-dir}")
    private String storageDir;

    private final List<Object> cleanup = new ArrayList<>();
    private final List<Path> createdFiles = new ArrayList<>();
    private final List<Path> createdDirs = new ArrayList<>();

    @Test
    void findsPhotoNamedAfterTheEmployeeId() throws IOException {
        Employee employee = createEmployee();
        Path photo = writePhoto(employee.getId() + ".jpg");

        Optional<FileRef> fileRef = employeePhotoService.findPhotoRef(employee.getId());
        assertTrue(fileRef.isPresent(), "photo named after the employee id must be found");
        assertEquals(photo.getFileName().toString(), fileRef.get().getFileName());

        // the reference must be usable by the file storage itself, not only by the index
        FileStorage fileStorage = fileStorageLocator.getDefault();
        assertTrue(fileStorage.fileExists(fileRef.get()));
    }

    @Test
    void readsPhotoContentAndContentType() throws IOException {
        Employee employee = createEmployee();
        writePhoto(employee.getId() + ".jpg");

        EmployeePhotoService.Photo photo = employeePhotoService.loadPhoto(employee.getId()).orElseThrow();

        assertArrayEquals(PHOTO_CONTENT, photo.content());
        assertEquals("image/jpeg", photo.contentType());
    }

    @Test
    void buildsPhotoUrlOnlyForAnEmployeeThatHasAPhoto() throws IOException {
        Employee withPhoto = createEmployee();
        Employee withoutPhoto = createEmployee();
        writePhoto(withPhoto.getId() + ".jpg");

        assertEquals("/employee-photos/" + withPhoto.getId(),
                employeePhotoService.getPhotoUrl(withPhoto.getId()));
        assertNull(employeePhotoService.getPhotoUrl(withoutPhoto.getId()));
        assertNull(employeePhotoService.getPhotoUrl(null));
    }

    @Test
    void ignoresAFileThatIsNotAnImage() throws IOException {
        Employee employee = createEmployee();
        writePhoto(employee.getId() + ".txt");

        assertTrue(employeePhotoService.findPhotoRef(employee.getId()).isEmpty());
        assertTrue(employeePhotoService.loadPhoto(employee.getId()).isEmpty());
    }

    @Test
    void returnsNothingForAnEmployeeWithoutAPhoto() {
        employeePhotoService.invalidateIndex();

        assertTrue(employeePhotoService.findPhotoRef(UUID.randomUUID()).isEmpty());
        assertTrue(employeePhotoService.findPhotoRef(null).isEmpty());
    }

    private Employee createEmployee() {
        Employee employee = dataManager.create(Employee.class);
        employee.setFullName("Photo Test " + UUID.randomUUID());
        Employee saved = dataManager.save(employee);
        cleanup.add(saved);
        return saved;
    }

    /**
     * Writes a file into a {@code yyyy/MM/dd} directory of the storage root — the only layout
     * {@code LocalFileStorage} can address — and drops the cached index so that the new file is
     * picked up right away. The date is one no upload can produce, so the fixture never shares
     * a directory with a real photo.
     */
    private Path writePhoto(String fileName) throws IOException {
        Path directory = Paths.get(storageDir.split(",")[0].trim(), FIXTURE_DATE_DIR);
        Files.createDirectories(directory);
        createdDirs.add(directory);

        Path file = directory.resolve(fileName);
        Files.write(file, PHOTO_CONTENT);
        createdFiles.add(file);

        employeePhotoService.invalidateIndex();
        return file;
    }

    /** A directory the fixture shares with another test only goes away once that test is done. */
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
        }
        for (Path directory : createdDirs) {
            deleteIfEmpty(directory);
            deleteIfEmpty(directory.getParent());
            deleteIfEmpty(directory.getParent().getParent());
        }
        createdFiles.clear();
        createdDirs.clear();

        cleanup.forEach(dataManager::remove);
        cleanup.clear();

        // the index must not keep pointing at the files the test has just removed
        employeePhotoService.invalidateIndex();
    }
}
