package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedMusicResult {
    private List<MusicMetadataDto> items;
    private int totalTracks;
    private int page;
    private int size;
}
