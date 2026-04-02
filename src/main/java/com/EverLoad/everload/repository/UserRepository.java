package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    // Solo bloquea si el usuario existe con un estado activo (no eliminado)
    boolean existsByUsernameAndStatusIn(String username, Collection<UserStatus> statuses);
    boolean existsByEmailAndStatusIn(String email, Collection<UserStatus> statuses);

    // Mantener para compatibilidad con UserDetailsService
    boolean existsByUsername(String username);

    List<User> findByStatus(UserStatus status);

    @Modifying
    @Query("DELETE FROM User u WHERE u.id = :id")
    void hardDeleteById(@Param("id") Long id);
}