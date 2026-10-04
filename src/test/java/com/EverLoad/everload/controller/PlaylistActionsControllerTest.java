package com.everload.everload.controller;

import com.everload.everload.model.*;
import com.everload.everload.repository.*;
import com.everload.everload.service.PlaylistImportService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlaylistActionsControllerTest {
    private final PlaylistRepository playlists = mock(PlaylistRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlaylistImportService importer = mock(PlaylistImportService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final UserDetails principal = mock(UserDetails.class);
    private final User user = User.builder().id(7L).username("reader").build();
    private final PlaylistActionsController controller = new PlaylistActionsController(playlists, users, importer, transactions);

    PlaylistActionsControllerTest() {
        when(principal.getUsername()).thenReturn("reader");
        when(users.findByUsername("reader")).thenReturn(Optional.of(user));
        when(transactions.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        when(playlists.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test void cannotReadOrDuplicateInaccessiblePlaylist() {
        when(playlists.findReadable(9L, user)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> controller.get(principal, 9L));
        assertThrows(ResponseStatusException.class, () -> controller.duplicate(principal, 9L));
        verify(playlists, never()).save(any());
    }

    @Test void duplicateIsPrivateOwnedCopyWithIndependentTracks() {
        Playlist original = Playlist.builder().id(9L).name("Original").isPublic(true).build();
        var track = PlaylistTrack.builder().id(15L).playlist(original).trackPath("abcdefghijk").title("Song").artist("Singer").source("youtube").nasPathId(0L).position(4).build();
        original.getTracks().add(track);
        when(playlists.findReadable(9L, user)).thenReturn(Optional.of(original));
        var copy = controller.duplicate(principal, 9L);
        assertFalse(copy.getIsPublic());
        assertSame(user, copy.getUser());
        assertTrue(copy.getCollaborators().isEmpty());
        assertNotSame(track, copy.getTracks().get(0));
        assertNull(copy.getTracks().get(0).getId());
        assertEquals("youtube", copy.getTracks().get(0).getSource());
        assertEquals(0, copy.getTracks().get(0).getPosition());
        assertSame(copy, copy.getTracks().get(0).getPlaylist());
    }

    @Test void failedImportDoesNotSavePlaylist() {
        when(importer.read("url")).thenThrow(new IllegalArgumentException());
        assertThrows(ResponseStatusException.class, () -> controller.importPlaylist(principal, new PlaylistActionsController.ImportRequest("url", "Name")));
        verify(playlists, never()).save(any());
    }

    @Test void importedTracksHavePositionsAndBelongToNewPrivatePlaylist() {
        when(importer.read("url")).thenReturn(new PlaylistImportService.Imported(List.of(
                PlaylistTrack.builder().title("Song").source("youtube").build()), 2, true));
        var response = controller.importPlaylist(principal, new PlaylistActionsController.ImportRequest("url", " Name "));
        var playlist = (Playlist) response.get("playlist");
        assertEquals("Name", playlist.getName());
        assertFalse(playlist.getIsPublic());
        assertSame(user, playlist.getUser());
        assertSame(playlist, playlist.getTracks().get(0).getPlaylist());
        assertEquals(0, playlist.getTracks().get(0).getPosition());
        assertEquals(2, response.get("skipped"));
    }
}
