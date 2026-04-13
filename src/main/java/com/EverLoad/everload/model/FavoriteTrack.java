package com.EverLoad.everload.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_tracks", uniqueConstraints = {
    @UniqueConstraint(
        name = "uc_favorite_user_track",
        columnNames = {"user_id", "track_path", "nas_path_id"}
    )
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String trackPath; // The relative path in the NAS, or some identifier

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;
    
    private Long nasPathId; // ID of the root NasPath

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
