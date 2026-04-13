package com.EverLoad.everload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "playback_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String trackPath;

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;

    private Long nasPathId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime playedAt;

    private Integer durationSeconds; // How long it was played
    private Boolean completed; // Did the track finish entirely?

    @PrePersist
    protected void onCreate() {
        playedAt = LocalDateTime.now();
    }
}
