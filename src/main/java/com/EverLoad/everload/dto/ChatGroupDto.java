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
    /** For PRIVATE chats: whether the partner is currently online. */
    private Boolean partnerOnline;
    /** For PRIVATE chats: partner's last seen (null if partner disabled showLastSeen or is online). */
    private LocalDateTime partnerLastSeen;
    /** For GROUP/ANNOUNCEMENT: number of members currently online. */
    private int onlineCount;
}