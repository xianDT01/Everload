package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.UpdateUserRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public List<UserDto> getPendingUsers() {
        return userRepository.findByStatus(UserStatus.PENDING)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<UserDto> getActiveUsers() {
        return userRepository.findByStatus(UserStatus.ACTIVE)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        return toDto(userRepository.save(user));
    }

    public void revokeAccess(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
    }

    public String getUsernameById(Long id) {
        return userRepository.findById(id)
                .map(User::getUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        // Usamos JPQL directo para evitar problemas de caché JPA de primer nivel
        userRepository.hardDeleteById(id);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(buildAvatarUrl(user.getAvatarFilename()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .online(presenceService.isOnline(user.getUsername()))
                // Admin view: always include lastSeen for full visibility
                .lastSeen(user.getLastSeen())
                .showLastSeen(!Boolean.FALSE.equals(user.getShowLastSeen()))
                .build();
    }

    private String buildAvatarUrl(String filename) {
        return (filename != null && !filename.isBlank())
                ? "/api/user/avatar/img/" + filename
                : null;
    }
}