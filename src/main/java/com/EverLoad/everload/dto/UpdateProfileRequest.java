package com.everload.everload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50)
    private String username;

    @Email
    private String email;

    /** null means "don't change". */
    private Boolean showLastSeen;

    @Pattern(regexp = "island|bar")
    private String floatingPlayerStyle;
}
