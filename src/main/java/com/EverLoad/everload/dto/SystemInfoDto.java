package com.EverLoad.everload.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemInfoDto {
    private String appVersion;
    /** Short SHA of the commit that was built into this running image. */
    private String currentCommit;
    private String javaVersion;
    private long uptimeSeconds;
    private String dbPath;
    private long dbSizeBytes;
    private String dbSizeFormatted;
}