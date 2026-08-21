package com.everload.everload.controller;

import com.everload.everload.model.Playlist;
import com.everload.everload.model.PlaylistCollaborator;
import com.everload.everload.model.PlaylistTrack;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.PlaylistCollaboratorRepository;
import com.everload.everload.repository.PlaylistRepository;
import com.everload.everload.repository.PlaylistTrackRepository;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaylistControllerTest {

    private PlaylistRepository playlistRepository;
    private PlaylistTrackRepository trackRepository;
    private PlaylistCollaboratorRepository collaboratorRepository;
    private UserRepository userRepository;
    private PlaylistController controller;
    private UserDetails principal;
    private User owner;

    @BeforeEach
    void setUp() {
        playlistRepository = mock(PlaylistRepository.class);
        trackRepository = mock(PlaylistTrackRepository.class);
        collaboratorRepository = mock(PlaylistCollaboratorRepository.class);
        userRepository = mock(UserRepository.class);
        controller = new PlaylistController(
                playlistRepository, trackRepository, collaboratorRepository, userRepository);
        principal = mock(UserDetails.class);
        owner = User.builder().id(1L).username("owner").status(UserStatus.ACTIVE).build();
        when(principal.getUsername()).thenReturn("owner");
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    }

    @Test
    void listCreateVisibilityRenameAndDeleteUseTheAuthenticatedOwner() {
        Playlist playlist = playlist(10L, "Original");
        when(playlistRepository.findByUserOrderByCreatedAtDesc(owner)).thenReturn(List.of(playlist));
        when(playlistRepository.findByIsPublicTrueOrderByCreatedAtDesc()).thenReturn(List.of(playlist));
        when(playlistRepository.findSharedWithUser(owner)).thenReturn(List.of(playlist));
        when(playlistRepository.findByIdAndUser(10L, owner)).thenReturn(Optional.of(playlist));
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, controller.list(principal).getBody().size());
        assertEquals(1, controller.listPublic().getBody().size());
        assertEquals(1, controller.listShared(principal).getBody().size());

        PlaylistController.CreatePlaylistDto create = new PlaylistController.CreatePlaylistDto();
        create.setName("  Road Trip  ");
        Playlist created = controller.create(principal, create).getBody();
        assertEquals("Road Trip", created.getName());
        assertSame(owner, created.getUser());

        PlaylistController.VisibilityDto visibility = new PlaylistController.VisibilityDto();
        visibility.setIsPublic(true);
        assertEquals(HttpStatus.OK, controller.setVisibility(principal, 10L, visibility).getStatusCode());
        assertTrue(playlist.getIsPublic());

        PlaylistController.CreatePlaylistDto rename = new PlaylistController.CreatePlaylistDto();
        rename.setName("  Renamed  ");
        assertEquals(HttpStatus.OK, controller.rename(principal, 10L, rename).getStatusCode());
        assertEquals("Renamed", playlist.getName());
        assertEquals(HttpStatus.OK, controller.delete(principal, 10L).getStatusCode());
        verify(playlistRepository).delete(playlist);
    }

    @Test
    void missingOwnedPlaylistReturnsNotFoundForMutations() {
        when(playlistRepository.findByIdAndUser(anyLong(), eq(owner))).thenReturn(Optional.empty());
        when(playlistRepository.findByIdAndEditableByUser(anyLong(), eq(owner))).thenReturn(Optional.empty());
        PlaylistController.CreatePlaylistDto name = new PlaylistController.CreatePlaylistDto();
        name.setName("Name");
        PlaylistController.VisibilityDto visibility = new PlaylistController.VisibilityDto();
        visibility.setIsPublic(false);
        PlaylistController.PlaylistTrackDto track = new PlaylistController.PlaylistTrackDto();
        PlaylistController.ReorderTracksDto order = new PlaylistController.ReorderTracksDto();

        assertEquals(HttpStatus.NOT_FOUND, controller.setVisibility(principal, 1L, visibility).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.rename(principal, 1L, name).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.delete(principal, 1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.addTrack(principal, 1L, track).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.removeTrack(principal, 1L, 2L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.reorderTracks(principal, 1L, order).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.listCollaborators(principal, 1L).getStatusCode());
    }

    @Test
    void tracksCanBeReusedAddedRemovedAndReordered() {
        Playlist playlist = playlist(10L, "Mix");
        PlaylistTrack first = PlaylistTrack.builder().id(1L).playlist(playlist).trackPath("a.mp3").position(0).build();
        PlaylistTrack second = PlaylistTrack.builder().id(2L).playlist(playlist).trackPath("b.mp3").position(1).build();
        playlist.setTracks(new ArrayList<>(List.of(first, second)));
        when(playlistRepository.findByIdAndEditableByUser(10L, owner)).thenReturn(Optional.of(playlist));
        when(trackRepository.existsByPlaylistAndTrackPathAndNasPathId(playlist, "a.mp3", 3L))
                .thenReturn(true);
        when(trackRepository.findByPlaylistAndTrackPathAndNasPathId(playlist, "a.mp3", 3L))
                .thenReturn(Optional.of(first));
        when(trackRepository.countByPlaylist(playlist)).thenReturn(2);
        when(trackRepository.save(any(PlaylistTrack.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trackRepository.findByIdAndPlaylist(2L, playlist)).thenReturn(Optional.of(second));

        PlaylistController.PlaylistTrackDto existing = trackDto("a.mp3", 3L);
        assertSame(first, controller.addTrack(principal, 10L, existing).getBody());

        PlaylistController.PlaylistTrackDto fresh = trackDto("c.mp3", 3L);
        PlaylistTrack created = (PlaylistTrack) controller.addTrack(principal, 10L, fresh).getBody();
        assertEquals("New Track", created.getTitle());
        assertEquals(2, created.getPosition());

        assertEquals(HttpStatus.OK, controller.removeTrack(principal, 10L, 2L).getStatusCode());
        verify(trackRepository).delete(second);

        PlaylistController.ReorderTracksDto order = new PlaylistController.ReorderTracksDto();
        order.setTrackIds(List.of(2L, 999L, 1L));
        ResponseEntity<Object> response = controller.reorderTracks(principal, 10L, order);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, first.getPosition());
        assertEquals(0, second.getPosition());
        verify(trackRepository).saveAll(List.of(second, first));
    }

    @Test
    void collaboratorSearchAddRemoveAndLeaveCoverOwnershipRules() {
        Playlist playlist = playlist(10L, "Shared");
        User alice = User.builder().id(2L).username("alice").status(UserStatus.ACTIVE).build();
        User bob = User.builder().id(3L).username("bob").status(UserStatus.ACTIVE).build();
        when(playlistRepository.findByIdAndUser(10L, owner)).thenReturn(Optional.of(playlist));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userRepository.findTop10ByUsernameContainingIgnoreCaseAndStatus("a", UserStatus.ACTIVE))
                .thenReturn(List.of(owner, alice));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(collaboratorRepository.findByPlaylist(playlist)).thenReturn(List.of(
                PlaylistCollaborator.builder().playlist(playlist).user(alice).build()));

        assertTrue(controller.searchUsers(principal, " ").getBody().isEmpty());
        assertEquals(List.of("alice"), controller.searchUsers(principal, " a ").getBody());

        PlaylistController.CollaboratorDto missing = new PlaylistController.CollaboratorDto();
        missing.setUsername("unknown");
        assertEquals(HttpStatus.BAD_REQUEST, controller.addCollaborator(principal, 10L, missing).getStatusCode());

        PlaylistController.CollaboratorDto self = new PlaylistController.CollaboratorDto();
        self.setUsername("owner");
        assertEquals(HttpStatus.BAD_REQUEST, controller.addCollaborator(principal, 10L, self).getStatusCode());

        PlaylistController.CollaboratorDto add = new PlaylistController.CollaboratorDto();
        add.setUsername("alice");
        assertEquals(List.of("alice"), controller.addCollaborator(principal, 10L, add).getBody());
        verify(collaboratorRepository).save(any(PlaylistCollaborator.class));

        when(collaboratorRepository.existsByPlaylistAndUser(playlist, bob)).thenReturn(true);
        PlaylistController.CollaboratorDto duplicate = new PlaylistController.CollaboratorDto();
        duplicate.setUsername("bob");
        assertEquals(HttpStatus.BAD_REQUEST, controller.addCollaborator(principal, 10L, duplicate).getStatusCode());

        assertEquals(HttpStatus.OK, controller.removeCollaborator(principal, 10L, "alice").getStatusCode());
        assertEquals(HttpStatus.OK, controller.leave(principal, 10L).getStatusCode());
        verify(collaboratorRepository).deleteByPlaylistAndUser(playlist, alice);
        verify(collaboratorRepository).deleteByPlaylistAndUser(playlist, owner);
    }

    private Playlist playlist(long id, String name) {
        return Playlist.builder().id(id).user(owner).name(name).build();
    }

    private PlaylistController.PlaylistTrackDto trackDto(String path, long nasPathId) {
        PlaylistController.PlaylistTrackDto dto = new PlaylistController.PlaylistTrackDto();
        dto.setTrackPath(path);
        dto.setNasPathId(nasPathId);
        dto.setTitle("New Track");
        dto.setArtist("Artist");
        dto.setAlbum("Album");
        dto.setDurationSeconds(180);
        return dto;
    }
}
