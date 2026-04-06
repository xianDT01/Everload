package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatGroupDto {
    private Long id;
    private String name;
    private String description;
    private String type;
    private LocalDateTime createdAt;
    private int memberCount;
    private long messageCount;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private String createdByUsername;
}
