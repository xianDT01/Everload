package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YtArtistDto {
    private String channelId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private List<YtTrackDto> topSongs;
    private List<YtAlbumDto> albums;
}
