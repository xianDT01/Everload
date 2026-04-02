package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.ChangePasswordRequest;
import com.EverLoad.everload.dto.UpdateProfileRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.security.JwtUtil;
import com.EverLoad.everload.security.UserDetailsServiceImpl;
import com.EverLoad.everload.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @GetMapping
    public ResponseEntity<UserDto> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(profileService.getProfile(userDetails.getUsername()));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        try {
            UserDto updated = profileService.updateProfile(userDetails.getUsername(), request);
            // Regenerate JWT with the (possibly new) username so the client stays authenticated
            UserDetails freshDetails = userDetailsService.loadUserByUsername(updated.getUsername());
            String newToken = jwtUtil.generateToken(freshDetails);
            return ResponseEntity.ok(Map.of("user", updated, "newToken", newToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        try {
            profileService.changePassword(userDetails.getUsername(), request);
            return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
