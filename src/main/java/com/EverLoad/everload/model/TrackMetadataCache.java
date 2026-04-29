package com.EverLoad.everload.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "track_metadata_cache",
    uniqueConstraints = @UniqueConstraint(columnNames = {"nasPathId", "relativePath"}),
    indexes = @Index(columnList = "nasPathId, relativePath")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackMetadataCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long nasPathId;

    @Column(nullable = false, length = 2048)
    private String relativePath;

    @Column(nullable = false)
    private long lastModified;

    private String title;
    private String artist;
    private String album;
    private String format;
    @Column(name = "release_year")
    private String year;
    private int duration;
    private boolean hasCover;
    private int bpm;
}
