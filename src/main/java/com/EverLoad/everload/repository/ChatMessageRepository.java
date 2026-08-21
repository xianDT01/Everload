package com.everload.everload.repository;

import com.everload.everload.model.ChatGroup;
import com.everload.everload.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByGroupOrderBySentAtAsc(ChatGroup group);

    List<ChatMessage> findTop100ByGroupOrderBySentAtDesc(ChatGroup group);

    long countByGroup(ChatGroup group);

    void deleteByGroup(ChatGroup group);

    List<ChatMessage> findByGroupAndContentContainingIgnoreCaseOrderBySentAtDesc(ChatGroup group, String query);
}