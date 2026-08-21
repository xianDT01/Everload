package com.everload.everload.repository;

import com.everload.everload.model.ChatGroup;
import com.everload.everload.model.ChatGroupRead;
import com.everload.everload.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatGroupReadRepository extends JpaRepository<ChatGroupRead, Long> {

    Optional<ChatGroupRead> findByUserAndGroup(User user, ChatGroup group);

    List<ChatGroupRead> findByGroup(ChatGroup group);
}
