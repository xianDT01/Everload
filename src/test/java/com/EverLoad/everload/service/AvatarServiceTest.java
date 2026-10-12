package com.everload.everload.service;

import com.everload.everload.model.User;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarServiceTest {

    @TempDir
    Path tempDir;

    private UserRepository users;
    private AvatarService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new AvatarService(users);
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "maxSizeMb", 5L);
    }

    @Test
    void fileMetadataFallbacksRemainStableForMissingImages() {
        Path missing = tempDir.resolve("missing.jpg");

        Long modified = ReflectionTestUtils.invokeMethod(service, "lastModifiedMillis", missing);
        String fingerprint = ReflectionTestUtils.invokeMethod(service, "imageFingerprint", missing);

        assertEquals(0L, modified);
        assertTrue(fingerprint.endsWith("missing.jpg"));
    }

    @Test
    void uploadsAvatarReplacesOldFileAndPersistsNewFilename() throws Exception {
        User user = User.builder().id(12L).username("xian").avatarFilename("old.jpg").build();
        Files.writeString(tempDir.resolve("old.jpg"), "old");
        when(users.findByUsername("xian")).thenReturn(Optional.of(user));
        MultipartFile upload = upload("photo.PNG", "image/png", new byte[]{1, 2, 3});

        String filename = service.uploadAvatar("xian", upload);

        assertTrue(filename.matches("avatar_12_[a-f0-9]{8}\\.png"));
        assertFalse(Files.exists(tempDir.resolve("old.jpg")));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(tempDir.resolve(filename)));
        assertEquals(filename, user.getAvatarFilename());
        verify(users).save(user);
    }

    @Test
    void rejectsEmptyUnsupportedOversizedAndUnknownUserUploads() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.uploadGroupAvatar(2L, null,
                upload("empty.png", "image/png", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.uploadGroupAvatar(2L, null,
                upload("script.svg", "image/svg+xml", new byte[]{1})));

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getContentType()).thenReturn("image/jpeg");
        when(oversized.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertThrows(IllegalArgumentException.class, () -> service.uploadGroupAvatar(2L, null, oversized));

        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadAvatar("missing", upload("photo.jpg", "image/jpeg", new byte[]{4})));
    }

    @Test
    void uploadGroupAvatarAndRemoveAvatarDeletePreviousFiles() throws Exception {
        User user = User.builder().id(7L).username("member").avatarFilename("user-old.jpg").build();
        Files.writeString(tempDir.resolve("group-old.jpg"), "group old");
        Files.writeString(tempDir.resolve("user-old.jpg"), "user old");
        when(users.findByUsername("member")).thenReturn(Optional.of(user));

        String groupFilename = service.uploadGroupAvatar(9L, "group-old.jpg",
                upload("cover.webp", "image/webp", new byte[]{9, 8}));
        service.removeAvatar("member");

        assertTrue(groupFilename.matches("group_9_[a-f0-9]{8}\\.webp"));
        assertTrue(Files.exists(tempDir.resolve(groupFilename)));
        assertFalse(Files.exists(tempDir.resolve("group-old.jpg")));
        assertFalse(Files.exists(tempDir.resolve("user-old.jpg")));
        assertNull(user.getAvatarFilename());
        verify(users).save(user);
    }

    @Test
    void avatarPathsRejectTraversalAndImageListingFiltersAndDeduplicates() throws Exception {
        assertThrows(SecurityException.class, () -> service.getAvatarPath("../../outside.jpg"));
        assertEquals(tempDir.resolve("user.jpg"), service.getAvatarPath("/user.jpg"));
        assertEquals(List.of(), service.listAvatarImageUrls());

        Files.write(tempDir.resolve("profile.jpg"), new byte[]{1, 2});
        Files.write(tempDir.resolve("duplicate.png"), new byte[]{1, 2});
        Path artists = Files.createDirectories(tempDir.resolve("artists"));
        Files.write(artists.resolve("portrait.webp"), new byte[]{3});
        Path automatic = Files.createDirectories(tempDir.resolve("artists-auto"));
        Files.write(automatic.resolve("cached.jpg"), new byte[]{4});
        Files.writeString(tempDir.resolve("notes.txt"), "not an image");

        List<String> urls = service.listAvatarImageUrls();

        assertEquals(2, urls.size());
        assertTrue(urls.contains("/api/user/avatar/img/artists/portrait.webp"));
        assertTrue(urls.stream().anyMatch(url -> url.equals("/api/user/avatar/img/profile.jpg")
                || url.equals("/api/user/avatar/img/duplicate.png")));
        assertFalse(urls.stream().anyMatch(url -> url.contains("artists-auto")));
    }

    private MultipartFile upload(String filename, String contentType, byte[] content) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(content.length == 0);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        return file;
    }
}
