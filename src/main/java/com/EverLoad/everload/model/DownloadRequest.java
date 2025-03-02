package com.EverLoad.everload.model;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "download_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String status;
}
