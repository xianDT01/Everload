package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A playable track surfaced from YouTube Music's public catalogue
 * (search results, discover shelves, mixes, public playlists). Anonymous
 * mode only — there is no library/likes/playlist-membership state here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YtTrackDto {
    private String videoId;
    private String title;
    private String artist;
    private List<String> artists;
    private String album;
    private String albumId;
    private int durationSeconds;
    private String thumbnailUrl;
}
