package com.EverLoad.everload.model;

import java.time.LocalDateTime;

public class DownloadLog {
    private LocalDateTime timestamp;
    private String platform;
    private String videoId;
    private boolean success;
    private String error;

    public DownloadLog(LocalDateTime timestamp, String platform, String videoId, boolean success, String error) {
        this.timestamp = timestamp;
        this.platform = platform;
        this.videoId = videoId;
        this.success = success;
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPlatform() {
        return platform;
    }

    public String getVideoId() {
        return videoId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
