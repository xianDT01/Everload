package com.EverLoad.everload.service;

import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AvatarServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fileMetadataFallbacksRemainStableForMissingImages() {
        AvatarService service = new AvatarService(mock(UserRepository.class));
        Path missing = tempDir.resolve("missing.jpg");

        Long modified = ReflectionTestUtils.invokeMethod(service, "lastModifiedMillis", missing);
        String fingerprint = ReflectionTestUtils.invokeMethod(service, "imageFingerprint", missing);

        assertEquals(0L, modified);
        assertTrue(fingerprint.endsWith("missing.jpg"));
    }
}
