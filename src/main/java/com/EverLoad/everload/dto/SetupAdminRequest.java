package com.everload.everload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupAdminRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,40}") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 72) String confirmation) {
    @Override public String toString() { return "SetupAdminRequest[REDACTED]"; }
}
