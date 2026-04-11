package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusicMetadataDto {
    private String name;
    private String path;
    private boolean directory;
    private long size;
    private String lastModified;
    
    // Audio specific metadata
    private String title;
    private String artist;
    private String album;
    private int duration; // in seconds
    private String format;
    private boolean hasCover;
    private int bpm;
}
