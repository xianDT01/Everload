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
public class YtAlbumDto {
    private String browseId;
    private String title;
    private String artist;
    private String year;
    private String thumbnailUrl;
    private List<YtTrackDto> tracks;
}
