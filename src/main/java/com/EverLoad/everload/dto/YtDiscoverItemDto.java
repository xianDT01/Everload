package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One tile in a discover shelf. {@code type} discriminates which of the
 * other fields are populated — kept as a single flat shape (rather than a
 * polymorphic hierarchy) since Jackson serialization stays simple and the
 * frontend only needs to switch on {@code type}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YtDiscoverItemDto {

    public enum Type { SONG, PLAYLIST, ALBUM, ARTIST, MOOD }

    private Type type;
    private String title;
    private String subtitle;
    private String thumbnailUrl;

    // SONG
    private YtTrackDto track;

    // PLAYLIST
    private String playlistId;

    // ALBUM
    private String browseId;

    // ARTIST
    private String channelId;

    // MOOD (genre/mood shelves, browseId starting with FEmusic_)
    private String moodBrowseId;
}
