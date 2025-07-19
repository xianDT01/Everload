package com.EverLoad.everload.model;

public class SpotifyResult {
    private String title;
    private String youtubeUrl;

    public SpotifyResult(String title, String youtubeUrl) {
        this.title = title;
        this.youtubeUrl = youtubeUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }
}