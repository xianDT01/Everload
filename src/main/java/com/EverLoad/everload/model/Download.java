package com.EverLoad.everload.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro del historial de descargas. Antes vivía en downloads_history.json
 * (el archivo entero se reescribía en cada descarga y crecía sin límite).
 */
@Entity
@Table(name = "download_history")
public class Download {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
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

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
