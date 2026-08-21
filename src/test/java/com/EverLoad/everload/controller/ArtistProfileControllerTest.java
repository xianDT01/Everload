package com.everload.everload.controller;

import com.everload.everload.model.ArtistProfile;
import com.everload.everload.model.User;
import com.everload.everload.repository.ArtistProfileRepository;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistProfileControllerTest {

    @TempDir
    Path tempDir;

    private ArtistProfileRepository artistRepository;
    private UserRepository userRepository;
    private ArtistProfileController controller;
    private UserDetails principal;
    private User owner;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistProfileRepository.class);
        userRepository = mock(UserRepository.class);
        controller = new ArtistProfileController(artistRepository, userRepository);
        ReflectionTestUtils.setField(controller, "avatarStoragePath", tempDir.toString());
        ReflectionTestUtils.setField(controller, "maxSizeMb", 1L);
        principal = mock(UserDetails.class);
        owner = User.builder().id(1L).username("owner").build();
        when(principal.getUsername()).thenReturn("owner");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(artistRepository.save(any(ArtistProfile.class))).thenAnswer(invocation -> {
            ArtistProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) profile.setId(100L);
            return profile;
        });
    }

    @Test
    void listCreateExistingCreateNewAndUpdateReturnNormalizedDtos() {
        ArtistProfile existing = profile(1L, "Existing");
        existing.setAliases(null);
        existing.setDescription(null);
        when(artistRepository.findAllByOrderByNameAsc()).thenReturn(List.of(existing));
        when(artistRepository.findFirstByNameIgnoreCaseOrderByIdAsc("Existing"))
                .thenReturn(Optional.of(existing));
        when(artistRepository.findFirstByNameIgnoreCaseOrderByIdAsc("New Artist"))
                .thenReturn(Optional.empty());
        when(artistRepository.findById(1L)).thenReturn(Optional.of(existing));

        Map<String, Object> listed = controller.list(principal).getBody().get(0);
        assertEquals("", listed.get("aliases"));
        assertEquals("", listed.get("description"));
        assertEquals("", listed.get("imageUrl"));

        ArtistProfileController.ArtistProfileDto duplicate = dto(" Existing ", "ignored", "ignored");
        assertEquals(1L, controller.create(principal, duplicate).getBody().get("id"));

        ArtistProfileController.ArtistProfileDto fresh = dto(" New Artist ", " Alias ", " Bio ");
        Map<String, Object> created = controller.create(principal, fresh).getBody();
        assertEquals("New Artist", created.get("name"));
        assertEquals("Alias", created.get("aliases"));

        ArtistProfileController.ArtistProfileDto update = dto(" Renamed ", null, " Updated ");
        assertEquals(HttpStatus.OK, controller.update(principal, 1L, update).getStatusCode());
        assertEquals("Renamed", existing.getName());
        assertEquals("", existing.getAliases());
        assertEquals("Updated", existing.getDescription());
    }

    @Test
    void invalidAndMissingProfilesReturnExpectedErrors() {
        when(artistRepository.findById(99L)).thenReturn(Optional.empty());
        ArtistProfileController.ArtistProfileDto blank = dto("   ", null, null);
        ArtistProfileController.ArtistImageUrlDto imageUrl = new ArtistProfileController.ArtistImageUrlDto();
        imageUrl.setImageUrl("https://example.test/image.jpg");
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> controller.create(principal, blank));
        assertEquals(HttpStatus.NOT_FOUND, controller.update(principal, 99L, blank).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.delete(principal, 99L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.uploadImage(principal, 99L, image).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.uploadImageFromUrl(principal, 99L, imageUrl).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.removeImage(principal, 99L).getStatusCode());
    }

    @Test
    void uploadImageStoresFileReplacesOldImageAndServesIt() throws Exception {
        Path imageDir = Files.createDirectories(tempDir.resolve("artists"));
        Files.write(imageDir.resolve("old.jpg"), new byte[]{9});
        ArtistProfile profile = profile(7L, "Artist");
        profile.setImageFilename("old.jpg");
        when(artistRepository.findById(7L)).thenReturn(Optional.of(profile));
        MockMultipartFile image = new MockMultipartFile(
                "image", "portrait.PNG", "image/png", new byte[]{1, 2, 3, 4});

        var response = controller.uploadImage(principal, 7L, image);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(Files.exists(imageDir.resolve("old.jpg")));
        assertNotNull(profile.getImageFilename());
        assertTrue(profile.getImageFilename().startsWith("artist_7_"));
        assertTrue(profile.getImageFilename().endsWith(".png"));
        assertTrue(Files.exists(imageDir.resolve(profile.getImageFilename())));
        assertEquals(HttpStatus.OK, controller.image(profile.getImageFilename()).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.image("../outside.png").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.image("missing.png").getStatusCode());
    }

    @Test
    void imageValidationRejectsEmptyWrongTypeAndOversizeFiles() {
        ArtistProfile profile = profile(2L, "Artist");
        when(artistRepository.findById(2L)).thenReturn(Optional.of(profile));
        MockMultipartFile empty = new MockMultipartFile("image", "a.png", "image/png", new byte[0]);
        MockMultipartFile wrong = new MockMultipartFile("image", "a.txt", "text/plain", new byte[]{1});
        MockMultipartFile large = new MockMultipartFile(
                "image", "a.png", "image/png", new byte[1024 * 1024 + 1]);

        assertEquals(HttpStatus.BAD_REQUEST, controller.uploadImage(principal, 2L, empty).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.uploadImage(principal, 2L, wrong).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.uploadImage(principal, 2L, large).getStatusCode());
        verify(artistRepository, times(0)).save(profile);
    }

    @Test
    void imageUrlRejectsBlankInsecureAndUnapprovedHosts() {
        ArtistProfile profile = profile(3L, "Artist");
        when(artistRepository.findById(3L)).thenReturn(Optional.of(profile));

        assertEquals(HttpStatus.BAD_REQUEST,
                controller.uploadImageFromUrl(principal, 3L, imageUrl(" ")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.uploadImageFromUrl(principal, 3L, imageUrl("http://cdn.dzcdn.net/a.jpg")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.uploadImageFromUrl(principal, 3L, imageUrl("https://dzcdn.net.evil.test/a.jpg")).getStatusCode());
    }

    @Test
    void removeAndDeleteProfileImagesUpdateDiskAndRepository() throws Exception {
        Path imageDir = Files.createDirectories(tempDir.resolve("artists"));
        Files.write(imageDir.resolve("remove.jpg"), new byte[]{1});
        ArtistProfile removable = profile(4L, "Removable");
        removable.setImageFilename("remove.jpg");
        when(artistRepository.findById(4L)).thenReturn(Optional.of(removable));

        assertEquals(HttpStatus.OK, controller.removeImage(principal, 4L).getStatusCode());
        assertNull(removable.getImageFilename());
        assertFalse(Files.exists(imageDir.resolve("remove.jpg")));

        Files.write(imageDir.resolve("delete.jpg"), new byte[]{2});
        ArtistProfile deletable = profile(5L, "Deletable");
        deletable.setImageFilename("delete.jpg");
        when(artistRepository.findById(5L)).thenReturn(Optional.of(deletable));
        assertEquals(HttpStatus.OK, controller.delete(principal, 5L).getStatusCode());
        assertFalse(Files.exists(imageDir.resolve("delete.jpg")));
        verify(artistRepository).delete(deletable);
    }

    @Test
    void helperMethodsNormalizeNamesAndFileExtensions() {
        String longName = "x".repeat(220);
        assertEquals(200, ((String) ReflectionTestUtils.invokeMethod(controller, "cleanName", longName)).length());
        assertEquals(".jpg", ReflectionTestUtils.invokeMethod(controller, "extension", (Object) null));
        assertEquals(".jpg", ReflectionTestUtils.invokeMethod(controller, "extension", "filename"));
        assertEquals(".webp", ReflectionTestUtils.invokeMethod(controller, "extension", "FILE.WEBP"));
        assertEquals(".png", ReflectionTestUtils.invokeMethod(controller, "extensionFromContentType", "image/png"));
        assertEquals(".webp", ReflectionTestUtils.invokeMethod(controller, "extensionFromContentType", "image/webp"));
        assertEquals(".gif", ReflectionTestUtils.invokeMethod(controller, "extensionFromContentType", "image/gif"));
        assertEquals(".jpg", ReflectionTestUtils.invokeMethod(controller, "extensionFromContentType", "image/jpeg"));
    }

    @Test
    void imageDeletionFailureDoesNotBreakProfileUpdate() throws Exception {
        Path storageFile = Files.writeString(tempDir.resolve("storage-file"), "not a directory");
        ReflectionTestUtils.setField(controller, "avatarStoragePath", storageFile.toString());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                controller, "deleteImage", "old-image.jpg"));
    }

    private ArtistProfile profile(long id, String name) {
        return ArtistProfile.builder().id(id).user(owner).name(name).build();
    }

    private ArtistProfileController.ArtistProfileDto dto(String name, String aliases, String description) {
        ArtistProfileController.ArtistProfileDto dto = new ArtistProfileController.ArtistProfileDto();
        dto.setName(name);
        dto.setAliases(aliases);
        dto.setDescription(description);
        return dto;
    }

    private ArtistProfileController.ArtistImageUrlDto imageUrl(String url) {
        ArtistProfileController.ArtistImageUrlDto dto = new ArtistProfileController.ArtistImageUrlDto();
        dto.setImageUrl(url);
        return dto;
    }
}
