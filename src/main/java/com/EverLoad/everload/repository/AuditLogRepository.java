package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByAdminUsernameContainingIgnoreCaseOrActionContainingIgnoreCaseOrTargetIdContainingIgnoreCase(
            String admin, String action, String target, Pageable pageable);
}