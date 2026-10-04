package com.everload.everload.dto;

import java.util.List;

public record CommunityDiscoverResponse(
        List<ArtistTrend> topArtists,
        List<TrackTrend> topTracks
) {
    public record ArtistTrend(String artist, long playCount) {}
    public record TrackTrend(String title, String artist, String album, long playCount) {}
}
