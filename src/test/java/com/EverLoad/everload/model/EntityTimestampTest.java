package com.EverLoad.everload.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityTimestampTest {

    @Test
    void persistenceCallbacksSetLocalTimestamps() {
        ArtistProfile artist = new ArtistProfile();
        artist.onCreate();
        assertNotNull(artist.getCreatedAt());
        assertNotNull(artist.getUpdatedAt());
        artist.onUpdate();

        AuditLog audit = new AuditLog();
        audit.onCreate();
        assertNotNull(audit.getTimestamp());

        ChatGroup group = new ChatGroup();
        group.onCreate();
        assertNotNull(group.getCreatedAt());

        ChatMessage message = new ChatMessage();
        message.onSend();
        assertNotNull(message.getSentAt());

        FavoriteTrack favorite = new FavoriteTrack();
        favorite.onCreate();
        assertNotNull(favorite.getCreatedAt());

        GroupMember member = new GroupMember();
        member.onJoin();
        assertNotNull(member.getJoinedAt());

        NasPath nasPath = new NasPath();
        nasPath.onCreate();
        assertNotNull(nasPath.getCreatedAt());

        Notification notification = new Notification();
        notification.onCreate();
        assertNotNull(notification.getCreatedAt());

        PlaybackHistory history = new PlaybackHistory();
        history.onCreate();
        assertNotNull(history.getPlayedAt());

        Playlist playlist = new Playlist();
        playlist.onCreate();
        assertNotNull(playlist.getCreatedAt());

        PlaylistCollaborator collaborator = new PlaylistCollaborator();
        collaborator.onAdd();
        assertNotNull(collaborator.getAddedAt());

        SnakeScore score = new SnakeScore();
        score.onCreate();
        assertNotNull(score.getPlayedAt());

        User user = new User();
        user.onCreate();
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertTrue(user.getShowLastSeen());
        user.onUpdate();
    }

    @Test
    void auditKeepsExplicitTimestamp() {
        AuditLog audit = new AuditLog();
        java.time.LocalDateTime timestamp = java.time.LocalDateTime.of(2025, java.time.Month.JANUARY, 2, 3, 4);
        audit.setTimestamp(timestamp);
        audit.onCreate();
        assertEquals(timestamp, audit.getTimestamp());
    }
}
