package com.everload.everload.model;

public class SpotifyResult {
    private String title;
    private String youtubeUrl;
    private String artist;

    public SpotifyResult(String title, String youtubeUrl, String artist) {
        this(title, youtubeUrl);
        this.artist = artist;
    }

    public String getArtist() { return artist; }

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
