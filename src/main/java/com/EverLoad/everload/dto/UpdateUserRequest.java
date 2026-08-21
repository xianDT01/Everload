package com.everload.everload.dto;

import com.everload.everload.model.Role;
import com.everload.everload.model.UserStatus;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private Role role;
    private UserStatus status;
}