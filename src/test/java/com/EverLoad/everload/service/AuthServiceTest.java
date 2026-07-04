package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AuthResponse;
import com.EverLoad.everload.dto.LoginRequest;
import com.EverLoad.everload.dto.RegisterRequest;
import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import com.EverLoad.everload.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de caracterización del flujo de registro/login: contratos que el
 * frontend y los filtros de seguridad asumen (estado PENDING al registrarse,
 * rechazo de cuentas no activas, token solo tras autenticación real).
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private AuthenticationManager authenticationManager;
    private UserDetailsService userDetailsService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        authenticationManager = mock(AuthenticationManager.class);
        userDetailsService = mock(UserDetailsService.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil,
                authenticationManager, userDetailsService);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setUsername("nuevo");
        r.setEmail("nuevo@test.local");
        r.setPassword("secreta123");
        return r;
    }

    private User activeUser() {
        return User.builder()
                .username("xian")
                .email("xian@test.local")
                .password("$2a$hash")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    @Test
    void register_creaUsuarioPendienteConRolBasicoYPasswordCodificada() {
        when(userRepository.existsByUsernameAndStatusIn(anyString(), any())).thenReturn(false);
        when(userRepository.existsByEmailAndStatusIn(anyString(), any())).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("ENCODED");

        AuthResponse response = authService.register(registerRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("ENCODED", saved.getValue().getPassword());
        assertEquals(Role.BASIC_USER, saved.getValue().getRole());
        assertEquals(UserStatus.PENDING, saved.getValue().getStatus());
        // El registro NUNCA devuelve token: la cuenta espera aprobación
        assertNull(response.getToken());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void register_rechazaUsernameYaEnUso() {
        when(userRepository.existsByUsernameAndStatusIn(eq("nuevo"), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rechazaEmailYaRegistrado() {
        when(userRepository.existsByUsernameAndStatusIn(anyString(), any())).thenReturn(false);
        when(userRepository.existsByEmailAndStatusIn(eq("nuevo@test.local"), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest()));
        verify(userRepository, never()).save(any());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_devuelveTokenYActualizaLastSeenParaCuentaActiva() {
        User user = activeUser();
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        UserDetails details = mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername("xian")).thenReturn(details);
        when(jwtUtil.generateToken(details)).thenReturn("TOKEN");

        LoginRequest request = new LoginRequest();
        request.setUsername("xian");
        request.setPassword("secreta123");
        AuthResponse response = authService.login(request);

        assertEquals("TOKEN", response.getToken());
        assertNotNull(user.getLastSeen());
        verify(userRepository).save(user);
    }

    @Test
    void login_credencialesInvalidasNoGeneraToken() {
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        LoginRequest request = new LoginRequest();
        request.setUsername("xian");
        request.setPassword("mala");

        assertThrows(IllegalArgumentException.class, () -> authService.login(request));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void login_cuentaPendienteRechazadaConEstadoDistinguible() {
        User pending = activeUser();
        pending.setStatus(UserStatus.PENDING);
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(pending));

        LoginRequest request = new LoginRequest();
        request.setUsername("xian");
        request.setPassword("secreta123");

        // IllegalState (403 en el controller), no IllegalArgument (401)
        assertThrows(IllegalStateException.class, () -> authService.login(request));
        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void refreshToken_soloParaCuentasActivas() {
        User pending = activeUser();
        pending.setStatus(UserStatus.PENDING);
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> authService.refreshToken("xian"));
    }
}
