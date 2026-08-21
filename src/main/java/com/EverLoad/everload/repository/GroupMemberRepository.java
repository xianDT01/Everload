package com.everload.everload.repository;

import com.everload.everload.model.ChatGroup;
import com.everload.everload.model.GroupMember;
import com.everload.everload.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupAndUser(ChatGroup group, User user);

    List<GroupMember> findByGroup(ChatGroup group);

    boolean existsByGroupAndUser(ChatGroup group, User user);

    long countByGroup(ChatGroup group);

    void deleteByGroup(ChatGroup group);
}