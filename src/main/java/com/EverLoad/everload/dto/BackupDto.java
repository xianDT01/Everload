package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupDto {
    private String name;
    private long sizeBytes;
    private String createdAt;
    private String type;
    private String description;

    /** Human-readable size, e.g. "2.4 MB" */
    public String getSizeFormatted() {
        if (sizeBytes < 1_024) return sizeBytes + " B";
        if (sizeBytes < 1_048_576) return String.format("%.1f KB", sizeBytes / 1_024.0);
        return String.format("%.1f MB", sizeBytes / 1_048_576.0);
    }
}
