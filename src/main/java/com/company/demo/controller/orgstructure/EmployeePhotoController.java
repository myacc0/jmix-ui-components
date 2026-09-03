package com.company.demo.controller.orgstructure;

import com.company.demo.service.orgstructure.EmployeePhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Serves employee photos stored in the Jmix file storage under the employee id, so that an
 * org chart node can reference its picture by URL instead of carrying the image bytes in the
 * chart JSON.
 * <p>
 * The endpoint is covered by the standard Jmix Flow UI security filter chain, so only an
 * authenticated user can read a photo.
 */
@RestController
public class EmployeePhotoController {

    private final EmployeePhotoService employeePhotoService;

    public EmployeePhotoController(EmployeePhotoService employeePhotoService) {
        this.employeePhotoService = employeePhotoService;
    }

    @GetMapping(EmployeePhotoService.PHOTO_URL_PREFIX + "{employeeId}")
    public ResponseEntity<byte[]> getEmployeePhoto(@PathVariable UUID employeeId) {
        return employeePhotoService.loadPhoto(employeeId)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .body(photo.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
