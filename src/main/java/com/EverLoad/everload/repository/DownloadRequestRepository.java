package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.DownloadRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadRequestRepository extends JpaRepository<DownloadRequest, Long> {
}
