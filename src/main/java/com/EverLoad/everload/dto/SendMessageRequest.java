package com.EverLoad.everload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private String content;
    private String messageType;
    private String videoId;
    private String videoTitle;
    private String thumbnailUrl;
    private String channelTitle;
}