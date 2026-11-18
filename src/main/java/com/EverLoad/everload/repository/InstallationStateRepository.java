package com.everload.everload.repository;

import com.everload.everload.model.InstallationState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InstallationStateRepository extends JpaRepository<InstallationState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InstallationState s where s.id = 1")
    InstallationState lockInstallation();
}
