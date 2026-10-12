package com.everload.everload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshSessionRequest {
    @NotBlank
    @Size(max = 128)
    private String refreshToken;
}
