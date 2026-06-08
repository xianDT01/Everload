package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YtStreamInfoDto {
    private String url;
    /** "webm" or "m4a" */
    private String format;
    private String userAgent;
    private Long contentLength;
    private Long durationSeconds;
    /** Which resolver produced this stream (e.g. "botguard", "yt-dlp") — useful for diagnostics. */
    private String resolvedBy;
}
