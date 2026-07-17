package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.UserRepository;
import com.EverLoad.everload.service.ChatService;
import com.EverLoad.everload.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private UserRepository userRepository;
    private PresenceService presenceService;
    private ChatController controller;
    private UserDetails principal;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        presenceService = mock(PresenceService.class);
        controller = new ChatController(mock(ChatService.class), userRepository, presenceService);
        principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("current");
    }

    @Test
    void activeUsersExcludeCurrentAndPendingAndRespectLastSeenPrivacy() {
        User current = user(1L, "current", UserStatus.ACTIVE);
        User offline = user(2L, "offline", UserStatus.ACTIVE);
        offline.setAvatarFilename("offline.jpg");
        offline.setShowLastSeen(true);
        offline.setLastSeen(LocalDateTime.of(2026, Month.JULY, 13, 8, 30));
        User online = user(3L, "online", UserStatus.ACTIVE);
        online.setLastSeen(LocalDateTime.of(2026, Month.JULY, 13, 8, 0));
        User pending = user(4L, "pending", UserStatus.PENDING);
        when(userRepository.findByUsername("current")).thenReturn(Optional.of(current));
        when(userRepository.findAll()).thenReturn(List.of(current, offline, online, pending));
        when(presenceService.isOnline("offline")).thenReturn(false);
        when(presenceService.isOnline("online")).thenReturn(true);

        List<Map<String, Object>> result = controller.getActiveUsers(principal).getBody();

        assertEquals(2, result.size());
        assertEquals("offline", result.get(0).get("username"));
        assertEquals("/api/user/avatar/img/offline.jpg", result.get(0).get("avatarUrl"));
        assertEquals("2026-07-13T08:30:00Z", result.get(0).get("lastSeen"));
        assertEquals("online", result.get(1).get("username"));
        assertNull(result.get(1).get("lastSeen"));
    }

    private User user(Long id, String username, UserStatus status) {
        return User.builder().id(id).username(username).status(status).build();
    }
}
