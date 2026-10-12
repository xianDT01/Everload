package com.everload.everload.controller;

import com.everload.everload.dto.UpdateUserRequest;
import com.everload.everload.dto.UserDto;
import com.everload.everload.model.Role;
import com.everload.everload.model.UserStatus;
import com.everload.everload.service.AuditLogService;
import com.everload.everload.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserManagementControllerTest {

    private UserService userService;
    private AuditLogService auditLogService;
    private UserManagementController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        auditLogService = mock(AuditLogService.class);
        controller = new UserManagementController(userService, auditLogService);
    }

    @Test
    void userLists_returnServiceResults() {
        List<UserDto> users = List.of(UserDto.builder().id(1L).username("listener").build());
        when(userService.getAllUsers()).thenReturn(users);
        when(userService.getPendingUsers()).thenReturn(users);
        when(userService.getActiveUsers()).thenReturn(users);

        assertSame(users, controller.getAllUsers().getBody());
        assertSame(users, controller.getPendingUsers().getBody());
        assertSame(users, controller.getActiveUsers().getBody());
    }

    @Test
    void updateUser_auditsApprovalAndCombinedChanges() {
        UserDto updated = UserDto.builder().username("listener").build();
        when(userService.updateUser(eq(9L), any())).thenReturn(updated);
        UpdateUserRequest request = request(Role.ADMIN, UserStatus.ACTIVE);

        assertSame(updated, controller.updateUser(9L, request).getBody());

        verify(auditLogService).log("USER_APPROVED", "User", "listener", "role=ADMIN, status=ACTIVE");
    }

    @Test
    void updateUser_auditsRejectedStatusRoleOnlyAndDefaultStatus() {
        when(userService.updateUser(eq(1L), any())).thenReturn(UserDto.builder().username("a").build());
        controller.updateUser(1L, request(null, UserStatus.REJECTED));
        verify(auditLogService).log("USER_REJECTED", "User", "a", "status=REJECTED");

        when(userService.updateUser(eq(2L), any())).thenReturn(UserDto.builder().username("b").build());
        controller.updateUser(2L, request(Role.NAS_USER, null));
        verify(auditLogService).log("USER_ROLE_CHANGED", "User", "b", "role=NAS_USER");

        when(userService.updateUser(eq(3L), any())).thenReturn(UserDto.builder().username("c").build());
        controller.updateUser(3L, request(null, UserStatus.PENDING));
        verify(auditLogService).log("USER_STATUS_CHANGED", "User", "c", "status=PENDING");
    }

    @Test
    void updateUser_returnsBadRequestWhenServiceRejectsInput() {
        when(userService.updateUser(eq(8L), any())).thenThrow(new IllegalArgumentException("invalid user"));

        var response = controller.updateUser(8L, request(null, null));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("invalid user", ((java.util.Map<?, ?>) response.getBody()).get("error"));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void revokeAndDeleteAuditSuccessfulActionsAndMapMissingUserToBadRequest() {
        when(userService.getUsernameById(1L)).thenReturn("listener");

        assertEquals(200, controller.revokeAccess(1L).getStatusCode().value());
        verify(userService).revokeAccess(1L);
        verify(auditLogService).log("USER_REVOKED", "User", "listener", null);

        assertEquals(200, controller.deleteUser(1L).getStatusCode().value());
        verify(userService).deleteUser(1L);
        verify(auditLogService).log("USER_DELETED", "User", "listener", null);

        when(userService.getUsernameById(2L)).thenThrow(new IllegalArgumentException("missing"));
        assertEquals(400, controller.revokeAccess(2L).getStatusCode().value());
        assertEquals(400, controller.deleteUser(2L).getStatusCode().value());
        verify(userService, never()).revokeAccess(2L);
        verify(userService, never()).deleteUser(2L);
    }

    private UpdateUserRequest request(Role role, UserStatus status) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(role);
        request.setStatus(status);
        return request;
    }
}
