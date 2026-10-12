package com.everload.everload.repository;

import com.everload.everload.model.PersistentSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface PersistentSessionRepository extends JpaRepository<PersistentSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PersistentSession s where s.tokenHash = :hash")
    Optional<PersistentSession> findForRotation(@Param("hash") String hash);

    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from PersistentSession s where s.expiresAt <= :now")
    void deleteExpired(@Param("now") Instant now);
}
