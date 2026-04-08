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
public class ChatGroupDto {
    private Long id;
    private String name;
    private String description;
    private String type;
    private LocalDateTime createdAt;
    private int memberCount;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private String imageFilename;
    private String createdByUsername;
    private String lastSenderAvatarUrl;
    private String privatePartnerUsername;
    private String privatePartnerAvatarUrl;
}