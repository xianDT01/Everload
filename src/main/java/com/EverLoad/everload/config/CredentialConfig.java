package com.EverLoad.everload.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CredentialConfig {
    private String clientId;
    private String clientSecret;
    private String youtubeApiKey;

    public CredentialConfig(
            @Value("${credentials.clientId:}") String clientId,
            @Value("${credentials.clientSecret:}") String clientSecret,
            @Value("${credentials.youtubeApiKey:}") String youtubeApiKey) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.youtubeApiKey = youtubeApiKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getYoutubeApiKey() {
        return youtubeApiKey;
    }

    public void setYoutubeApiKey(String youtubeApiKey) {
        this.youtubeApiKey = youtubeApiKey;
    }
}
