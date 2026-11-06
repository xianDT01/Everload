package com.everload.everload.controller;

import com.everload.everload.service.HlsStreamService;
import com.everload.everload.service.MetadataSnapshotService;
import com.everload.everload.service.MusicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicMetadataAuthorizationTest {
    private AnnotationConfigApplicationContext context;
    private MusicController controller;
    private MusicService music;
    private MetadataSnapshotService snapshots;
    private MusicController.MetadataUpdateRequest request;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(SecurityTestConfig.class);
        controller = context.getBean(MusicController.class);
        music = context.getBean(MusicService.class);
        snapshots = context.getBean(MetadataSnapshotService.class);
        request = new MusicController.MetadataUpdateRequest();
        request.setPathId(1L);
        request.setRelativePath("song.mp3");
        request.setTitle("Song");
        request.setArtist("Artist");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "NAS_USER"})
    void permitsMetadataAndCoversForNasManagers(String role) {
        authenticate(role);
        assertEquals(200, controller.updateMetadata(request).getStatusCode().value());
        assertEquals(200, controller.updateCoverArt(1L, "song.mp3", cover()).getStatusCode().value());
        verify(music).updateMetadata(1L, "song.mp3", "Song", "Artist", null, null);
        verify(snapshots).refreshTrack(1L, "song.mp3", "Song", "Artist", null);
        verify(music).updateCoverArt(eq(1L), eq("song.mp3"), any(byte[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASIC_USER", "USER"})
    void deniesMetadataAndCoversBeforeCallingServices(String role) {
        authenticate(role);
        assertThrows(AccessDeniedException.class, () -> controller.updateMetadata(request));
        assertThrows(AccessDeniedException.class, () -> controller.updateCoverArt(1L, "song.mp3", cover()));
        verifyNoInteractions(music, snapshots);
    }

    @Test
    void rejectsAnonymousEdits() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.updateMetadata(request));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.updateCoverArt(1L, "song.mp3", cover()));
        verifyNoInteractions(music, snapshots);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", null,
                        AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    private MockMultipartFile cover() {
        return new MockMultipartFile("image", "cover.png", "image/png", new byte[]{1, 2, 3});
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityTestConfig {
        @Bean MusicService musicService() { return mock(MusicService.class); }
        @Bean MetadataSnapshotService metadataSnapshotService() { return mock(MetadataSnapshotService.class); }
        @Bean MusicController musicController(MusicService music, MetadataSnapshotService snapshots) {
            return new MusicController(music, mock(HlsStreamService.class), mock(RestTemplate.class), snapshots);
        }
    }
}
