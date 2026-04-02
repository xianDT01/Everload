package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.ChatGroup;
import com.EverLoad.everload.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByGroupOrderBySentAtAsc(ChatGroup group);

    List<ChatMessage> findTop100ByGroupOrderBySentAtDesc(ChatGroup group);
}