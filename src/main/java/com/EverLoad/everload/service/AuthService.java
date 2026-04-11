package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AuthResponse;
import com.EverLoad.everload.dto.LoginRequest;
import com.EverLoad.everload.dto.RegisterRequest;
import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.EverLoad.everload.repository.UserRepository;
import com.EverLoad.everload.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthResponse register(RegisterRequest request) {
        // Solo bloquea si existe un usuario activo o pendiente (no registros eliminados)
        if (userRepository.existsByUsernameAndStatusIn(
                request.getUsername(), List.of(UserStatus.ACTIVE, UserStatus.PENDING))) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        }
        if (userRepository.existsByEmailAndStatusIn(
                request.getEmail(), List.of(UserStatus.ACTIVE, UserStatus.PENDING))) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.BASIC_USER)
                .status(UserStatus.PENDING)
                .build();

        userRepository.save(user);

        return AuthResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException("Credenciales incorrectas o cuenta no activa");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (user.getStatus() == UserStatus.PENDING) {
            throw new IllegalStateException("Tu cuenta está pendiente de aprobación por un administrador");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new IllegalStateException("Tu solicitud de acceso fue rechazada");
        }

        // Record login time as lastSeen (always UTC so serialization is unambiguous)
        user.setLastSeen(LocalDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        String avatarUrl = (user.getAvatarFilename() != null && !user.getAvatarFilename().isBlank())
                ? "/api/user/avatar/img/" + user.getAvatarFilename()
                : null;

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus().name())
                .avatarUrl(avatarUrl)
                .build();
    }
}