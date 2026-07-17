package com.EverLoad.everload.controller;

import com.EverLoad.everload.repository.ArtistProfileRepository;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class ArtistProfileControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void imageDeletionFailureDoesNotBreakProfileUpdate() throws Exception {
        Path storageFile = Files.writeString(tempDir.resolve("storage-file"), "not a directory");
        ArtistProfileController controller = new ArtistProfileController(
                mock(ArtistProfileRepository.class), mock(UserRepository.class));
        ReflectionTestUtils.setField(controller, "avatarStoragePath", storageFile.toString());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                controller, "deleteImage", "old-image.jpg"));
    }
}
