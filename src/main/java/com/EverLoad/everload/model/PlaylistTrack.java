package com.everload.everload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "playlist_tracks")
@Data
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    @JsonIgnore
    private Playlist playlist;

    @Column(nullable = false)
    private String trackPath;

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;

    @Builder.Default
    private String source = "nas";

    @Column(nullable = false)
    private Long nasPathId;

    private Integer durationSeconds;

    @Column(nullable = false)
    private Integer position;
}
