package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.ChangePasswordRequest;
import com.EverLoad.everload.dto.UpdateProfileRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return toDto(user);
    }

    public UserDto updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsernameAndStatusIn(newUsername, List.of(UserStatus.ACTIVE, UserStatus.PENDING))) {
                    throw new IllegalArgumentException("El nombre de usuario ya está en uso");
                }
                user.setUsername(newUsername);
            }
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.existsByEmailAndStatusIn(newEmail, List.of(UserStatus.ACTIVE, UserStatus.PENDING))) {
                    throw new IllegalArgumentException("El email ya está en uso");
                }
                user.setEmail(newEmail);
            }
        }

        if (request.getShowLastSeen() != null) {
            user.setShowLastSeen(request.getShowLastSeen());
        }

        return toDto(userRepository.save(user));
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarFilename() != null && !user.getAvatarFilename().isBlank()
                        ? "/api/user/avatar/img/" + user.getAvatarFilename()
                        : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastSeen(user.getLastSeen())
                .showLastSeen(!Boolean.FALSE.equals(user.getShowLastSeen()))
                .build();
    }
}
