package com.EverLoad.everload.model;

import java.time.LocalDateTime;

public class Download {
    private String title;
    private String type;       // "music" or "video"
    private String platform;   // "YouTube", "Spotify", "TikTok", etc.
    private LocalDateTime createdAt;

    public Download() {
        this.createdAt = LocalDateTime.now();
    }

    public Download(String title, String type, String platform) {
        this.title = title;
        this.type = type;
        this.platform = platform;
        this.createdAt = LocalDateTime.now();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}