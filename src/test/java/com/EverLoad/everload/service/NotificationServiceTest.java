package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.NotificationDto;
import com.EverLoad.everload.model.Notification;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.model.UserStatus;
import com.EverLoad.everload.repository.NotificationRepository;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        service = new NotificationService(notificationRepository, userRepository);
    }

    @Test
    void createNotificationPersistsAndMapsAllFields() {
        User user = user(1L, UserStatus.ACTIVE);
        LocalDateTime createdAt = LocalDateTime.of(2026, Month.JULY, 13, 10, 0);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(9L);
            saved.setCreatedAt(createdAt);
            return saved;
        });

        NotificationDto dto = service.createNotification(
                user, "download_complete", "Ready", "Finished", "/downloads"
        );

        assertEquals(9L, dto.getId());
        assertEquals("download_complete", dto.getType());
        assertEquals("Ready", dto.getTitle());
        assertEquals("Finished", dto.getMessage());
        assertEquals("/downloads", dto.getActionUrl());
        assertEquals(createdAt, dto.getCreatedAt());
        assertFalse(dto.isRead());
    }

    @Test
    void notificationsAreMappedAndLimitedToFifty() {
        User user = user(1L, UserStatus.ACTIVE);
        List<Notification> notifications = IntStream.range(0, 55)
                .mapToObj(i -> notification((long) i, user, i == 0))
                .toList();
        when(notificationRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(notifications);

        List<NotificationDto> result = service.getNotificationsForUser(user);

        assertEquals(50, result.size());
        assertTrue(result.get(0).isRead());
        assertEquals("Title 49", result.get(49).getTitle());
    }

    @Test
    void unreadAndBulkReadDelegateToRepository() {
        User user = user(1L, UserStatus.ACTIVE);
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(4L);

        assertEquals(4L, service.getUnreadCount(user));
        service.markAllRead(user);

        verify(notificationRepository).markAllReadByUser(user);
    }

    @Test
    void markReadOnlyUpdatesNotificationsOwnedByUser() {
        User owner = user(1L, UserStatus.ACTIVE);
        User other = user(2L, UserStatus.ACTIVE);
        Notification owned = notification(10L, owner, false);
        Notification foreign = notification(11L, other, false);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(owned));
        when(notificationRepository.findById(11L)).thenReturn(Optional.of(foreign));
        when(notificationRepository.findById(12L)).thenReturn(Optional.empty());

        service.markRead(10L, owner);
        service.markRead(11L, owner);
        service.markRead(12L, owner);

        assertTrue(owned.isRead());
        assertFalse(foreign.isRead());
        verify(notificationRepository).save(owned);
        verify(notificationRepository, never()).save(foreign);
    }

    @Test
    void broadcastCreatesNotificationsOnlyForActiveUsers() {
        User first = user(1L, UserStatus.ACTIVE);
        User pending = user(2L, UserStatus.PENDING);
        User second = user(3L, UserStatus.ACTIVE);
        when(userRepository.findAll()).thenReturn(List.of(first, pending, second));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createForAllActiveUsers("admin_notice", "Maintenance", "Tonight");

        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    private User user(Long id, UserStatus status) {
        return User.builder().id(id).username("user-" + id).status(status).build();
    }

    private Notification notification(Long id, User user, boolean read) {
        return Notification.builder()
                .id(id)
                .user(user)
                .type("admin_notice")
                .title("Title " + id)
                .message("Message")
                .read(read)
                .createdAt(LocalDateTime.of(2026, Month.JULY, 13, 10, 0))
                .actionUrl("/notice")
                .build();
    }
}
