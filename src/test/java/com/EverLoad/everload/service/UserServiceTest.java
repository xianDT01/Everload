package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.UpdateUserRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.model.Role;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository repository;
    private PresenceService presenceService;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        presenceService = mock(PresenceService.class);
        service = new UserService(repository, presenceService);
    }

    @Test
    void listMethodsMapPresenceAvatarAndPrivacy() {
        User user = user(1L, UserStatus.ACTIVE);
        user.setAvatarFilename("avatar.jpg");
        user.setShowLastSeen(false);
        when(repository.findByStatus(UserStatus.PENDING)).thenReturn(List.of(user));
        when(repository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));
        when(repository.findAll()).thenReturn(List.of(user));
        when(presenceService.isOnline("xian")).thenReturn(true);

        UserDto pending = service.getPendingUsers().get(0);
        assertEquals(1, service.getActiveUsers().size());
        assertEquals(1, service.getAllUsers().size());
        assertEquals("/api/user/avatar/img/avatar.jpg", pending.getAvatarUrl());
        assertTrue(pending.isOnline());
        assertFalse(pending.isShowLastSeen());
    }

    @Test
    void updateAndRevokePersistAdministrativeChanges() {
        User user = user(1L, UserStatus.PENDING);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(Role.ADMIN);
        request.setStatus(UserStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        UserDto updated = service.updateUser(1L, request);
        service.revokeAccess(1L);

        assertEquals(Role.ADMIN, updated.getRole());
        assertEquals(UserStatus.REJECTED, user.getStatus());
        verify(repository, times(2)).save(user);
    }

    @Test
    void usernameAndDeleteHandleExistingAndMissingUsers() {
        User user = user(1L, UserStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.existsById(1L)).thenReturn(true);

        assertEquals("xian", service.getUsernameById(1L));
        service.deleteUser(1L);
        verify(repository).hardDeleteById(1L);

        when(repository.findById(2L)).thenReturn(Optional.empty());
        when(repository.existsById(2L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getUsernameById(2L));
        assertThrows(IllegalArgumentException.class, () -> service.deleteUser(2L));
    }

    private User user(Long id, UserStatus status) {
        return User.builder()
                .id(id)
                .username("xian")
                .email("xian@example.test")
                .role(Role.BASIC_USER)
                .status(status)
                .build();
    }
}
