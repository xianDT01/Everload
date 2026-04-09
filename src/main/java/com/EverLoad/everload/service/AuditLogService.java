package com.EverLoad.everload.service;

import com.EverLoad.everload.model.AuditLog;
import com.EverLoad.everload.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * Logs an admin action. Uses the current authenticated user as the admin.
     *
     * @param action       e.g. "USER_APPROVED", "MESSAGE_DELETED"
     * @param targetEntity e.g. "User", "ChatMessage"
     * @param targetId     e.g. the username or ID of the affected resource
     * @param detail       optional human-readable context
     */
    public void log(String action, String targetEntity, String targetId, String detail) {
        try {
            String admin = resolveAdmin();
            AuditLog entry = AuditLog.builder()
                    .adminUsername(admin)
                    .action(action)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .detail(detail)
                    .build();
            repository.save(entry);
            log.info("[AUDIT] {} → {} {} ({}) detail={}", admin, action, targetEntity, targetId, detail);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: {}", e.getMessage());
        }
    }

    public Page<AuditLog> getPage(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        if (search != null && !search.isBlank()) {
            return repository.findByAdminUsernameContainingIgnoreCaseOrActionContainingIgnoreCaseOrTargetIdContainingIgnoreCase(
                    search, search, search, pageable);
        }
        return repository.findAllByOrderByTimestampDesc(pageable);
    }

    private String resolveAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "system";
    }
}