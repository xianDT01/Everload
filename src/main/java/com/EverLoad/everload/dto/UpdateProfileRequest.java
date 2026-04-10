package com.EverLoad.everload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50)
    private String username;

    @Email
    private String email;

    /** null means "don't change". */
    private Boolean showLastSeen;
}