package com.everload.everload.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LogoutSessionRequest {
    @Size(max = 128)
    private String refreshToken;
}
