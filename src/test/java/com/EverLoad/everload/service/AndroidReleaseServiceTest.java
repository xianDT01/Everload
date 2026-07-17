package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AndroidReleaseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidReleaseServiceTest {

    @TempDir
    Path tempDir;

    private AndroidReleaseService service;

    @BeforeEach
    void setUp() {
        service = new AndroidReleaseService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "releasePath", tempDir.toString());
    }

    @Test
    void emptyReleaseUsesSafeDefaults() {
        AndroidReleaseDto release = service.getRelease();

        assertFalse(release.isAvailable());
        assertEquals(0, release.getSizeBytes());
        assertEquals("", release.getFileName());
        assertThrows(IllegalArgumentException.class, service::getApkResource);
    }

    @Test
    void saveReleasePersistsApkAndMetadata() throws Exception {
        MockMultipartFile apk = new MockMultipartFile(
                "file", "EverLoad 2.apk", "application/vnd.android.package-archive", "apk-data".getBytes());

        AndroidReleaseDto release = service.saveRelease(apk, "2.0", "20", "Android 10+", "Changes");

        assertTrue(release.isAvailable());
        assertEquals("2.0", release.getVersionName());
        assertEquals("EverLoad 2.apk", service.getDownloadFileName());
        assertTrue(service.getApkResource().exists());
        assertTrue(Files.exists(tempDir.resolve("android-release.json")));

        service.deleteRelease();
        assertFalse(service.getRelease().isAvailable());
    }

    @Test
    void saveReleaseRejectsEmptyAndNonApkUploads() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.apk", "application/octet-stream", new byte[0]);
        MockMultipartFile text = new MockMultipartFile("file", "notes.txt", "text/plain", "data".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> service.saveRelease(empty, "", "", "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveRelease(text, "", "", "", ""));
    }

    @Test
    void downloadNameAndSanitizerUseDefaultForInvalidMetadata() throws Exception {
        Files.writeString(tempDir.resolve("android-release.json"), "{\"fileName\":\"release.txt\"}");

        assertEquals("everload.apk", service.getDownloadFileName());
        assertEquals("everload.apk", ReflectionTestUtils.invokeMethod(service, "sanitizeFileName", " "));
        assertEquals("everload.apk", ReflectionTestUtils.invokeMethod(service, "sanitizeFileName", (Object) null));
    }
}
