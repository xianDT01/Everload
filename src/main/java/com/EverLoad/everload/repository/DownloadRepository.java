package com.everload.everload.repository;

import com.everload.everload.model.Download;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DownloadRepository extends JpaRepository<Download, Long> {
    long countByCreatedAtGreaterThanEqual(LocalDateTime start);
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
    List<Download> findTop6ByOrderByCreatedAtDesc();
}
