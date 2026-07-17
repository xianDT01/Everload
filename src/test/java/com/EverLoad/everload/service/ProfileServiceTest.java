package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.ChangePasswordRequest;
import com.EverLoad.everload.dto.UpdateProfileRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new ProfileService(userRepository, passwordEncoder);
    }

    @Test
    void getProfileMapsAvatarAndPrivacySettings() {
        User user = user();
        user.setAvatarFilename(" profile.jpg ");
        user.setShowLastSeen(false);
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));

        UserDto profile = service.getProfile("xian");

        assertEquals("/api/user/avatar/img/ profile.jpg ", profile.getAvatarUrl());
        assertFalse(profile.isShowLastSeen());
        assertEquals(Role.ADMIN, profile.getRole());
    }

    @Test
    void missingProfileIsRejected() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getProfile("missing")
        );

        assertEquals("Usuario no encontrado", error.getMessage());
    }

    @Test
    void updateProfileTrimsAndPersistsChangedFields() {
        User user = user();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("  xian-new  ");
        request.setEmail("  new@example.test  ");
        request.setShowLastSeen(false);
        List<UserStatus> visibleStatuses = List.of(UserStatus.ACTIVE, UserStatus.PENDING);
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndStatusIn("xian-new", visibleStatuses)).thenReturn(false);
        when(userRepository.existsByEmailAndStatusIn("new@example.test", visibleStatuses)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        UserDto updated = service.updateProfile("xian", request);

        assertEquals("xian-new", updated.getUsername());
        assertEquals("new@example.test", updated.getEmail());
        assertFalse(updated.isShowLastSeen());
        verify(userRepository).save(user);
    }

    @Test
    void duplicateUsernameAndEmailAreRejected() {
        User user = user();
        UpdateProfileRequest usernameRequest = new UpdateProfileRequest();
        usernameRequest.setUsername("taken");
        UpdateProfileRequest emailRequest = new UpdateProfileRequest();
        emailRequest.setEmail("taken@example.test");
        List<UserStatus> visibleStatuses = List.of(UserStatus.ACTIVE, UserStatus.PENDING);
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndStatusIn("taken", visibleStatuses)).thenReturn(true);
        when(userRepository.existsByEmailAndStatusIn("taken@example.test", visibleStatuses)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.updateProfile("xian", usernameRequest));
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile("xian", emailRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    void unchangedAndBlankValuesDoNotTriggerUniquenessChecks() {
        User user = user();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("xian");
        request.setEmail(" ");
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserDto updated = service.updateProfile("xian", request);

        assertEquals("xian", updated.getUsername());
        assertEquals("xian@example.test", updated.getEmail());
        assertTrue(updated.isShowLastSeen());
        verify(userRepository, never()).existsByUsernameAndStatusIn(any(), any());
        verify(userRepository, never()).existsByEmailAndStatusIn(any(), any());
    }

    @Test
    void changePasswordValidatesCurrentPasswordAndStoresEncodedValue() {
        User user = user();
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("current");
        request.setNewPassword("new-secret");
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "encoded-current")).thenReturn(true);
        when(passwordEncoder.encode("new-secret")).thenReturn("encoded-new");

        service.changePassword("xian", request);

        assertEquals("encoded-new", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void incorrectCurrentPasswordDoesNotPersist() {
        User user = user();
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("new-secret");
        when(userRepository.findByUsername("xian")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-current")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.changePassword("xian", request));
        verify(userRepository, never()).save(any());
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("xian")
                .email("xian@example.test")
                .password("encoded-current")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .showLastSeen(true)
                .build();
    }
}
