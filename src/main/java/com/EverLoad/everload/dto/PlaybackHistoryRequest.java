package com.EverLoad.everload.dto;

/** Input accepted when a user records playback. Ownership and timestamps stay server-controlled. */
public record PlaybackHistoryRequest(
        String trackPath,
        String title,
        String artist,
        String album,
        Long nasPathId,
        Integer durationSeconds,
        Boolean completed
) {}
