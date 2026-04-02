package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.ChatGroup;
import com.EverLoad.everload.model.GroupMember;
import com.EverLoad.everload.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupAndUser(ChatGroup group, User user);

    List<GroupMember> findByGroup(ChatGroup group);

    boolean existsByGroupAndUser(ChatGroup group, User user);

    long countByGroup(ChatGroup group);
}