package com.EverLoad.everload.dto;

/** Input accepted when a user toggles a favorite. Ownership and database fields stay server-controlled. */
public record FavoriteTrackRequest(
        String trackPath,
        String title,
        String artist,
        String album,
        Long nasPathId
) {}
