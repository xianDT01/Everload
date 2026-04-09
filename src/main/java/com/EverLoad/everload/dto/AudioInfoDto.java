package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioInfoDto {
    private String filename;
    private String formatName;   // e.g. "mp3", "flac"
    private String extension;    // e.g. "mp3"
    private double durationSeconds;
    private long fileSizeBytes;
    private int bitrateKbps;
    private int sampleRate;
    private int channels;
}
