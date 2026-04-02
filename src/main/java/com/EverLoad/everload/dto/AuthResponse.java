package com.EverLoad.everload.dto;

import com.EverLoad.everload.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private Role role;
    private String status;
    private String avatarUrl;
}