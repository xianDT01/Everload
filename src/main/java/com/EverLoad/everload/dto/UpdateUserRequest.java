package com.EverLoad.everload.dto;

import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.UserStatus;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private Role role;
    private UserStatus status;
}