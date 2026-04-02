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
public class ChatMessageDto {
    private Long id;
    private Long groupId;
    private String senderUsername;
    private String senderAvatarUrl;
    private String content;
    private LocalDateTime sentAt;
    private boolean edited;
}