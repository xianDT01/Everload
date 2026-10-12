package com.everload.everload.security;

import com.everload.everload.model.Role;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    private UserRepository userRepository;
    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserDetailsServiceImpl(userRepository);
    }

    @Test
    void activeAccountLoadsWithRoleAndCredentials() {
        when(userRepository.findByUsername("listener")).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        var details = service.loadUserByUsername("listener");

        assertEquals("listener", details.getUsername());
        assertEquals("encoded-password", details.getPassword());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BASIC_USER")));
    }

    @Test
    void pendingAccountIsLockedButNotDisabled() {
        when(userRepository.findByUsername("listener")).thenReturn(Optional.of(user(UserStatus.PENDING)));

        var details = service.loadUserByUsername("listener");

        assertFalse(details.isAccountNonLocked());
        assertTrue(details.isEnabled());
    }

    @Test
    void rejectedAccountIsLockedAndDisabled() {
        when(userRepository.findByUsername("listener")).thenReturn(Optional.of(user(UserStatus.REJECTED)));

        var details = service.loadUserByUsername("listener");

        assertFalse(details.isAccountNonLocked());
        assertFalse(details.isEnabled());
    }

    @Test
    void missingAccountThrowsUsernameNotFound() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
    }

    private User user(UserStatus status) {
        return User.builder()
                .username("listener")
                .password("encoded-password")
                .role(Role.BASIC_USER)
                .status(status)
                .build();
    }
}
