package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AndroidReleaseDto {
    private boolean available;
    private String versionName;
    private String versionCode;
    private String minAndroidVersion;
    private String releaseNotes;
    private String fileName;
    private long sizeBytes;
    private String sizeFormatted;
    private String uploadedAt;
    private String downloadUrl;
}
