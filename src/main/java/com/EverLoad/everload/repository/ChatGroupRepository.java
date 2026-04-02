package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.ChatGroup;
import com.EverLoad.everload.model.GroupType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {

    @Query("SELECT g FROM ChatGroup g JOIN GroupMember m ON m.group = g WHERE m.user.id = :userId ORDER BY g.createdAt DESC")
    List<ChatGroup> findGroupsByUserId(@Param("userId") Long userId);

    Optional<ChatGroup> findFirstByType(GroupType type);

    List<ChatGroup> findAllByType(GroupType type);
}