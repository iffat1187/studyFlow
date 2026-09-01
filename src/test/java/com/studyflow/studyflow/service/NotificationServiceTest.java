package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase A service-level tests.
 *
 * Exercises {@link NotificationServiceImpl} with mocked repositories so the
 * creation, user-scoped retrieval, unread filtering and null/unknown handling
 * can be verified without a browser. The real SQL behind each repository
 * method is verified against the live database in
 * {@code NotificationQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final String ALICE_EMAIL = "alice@example.com";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).name("Alice").email(ALICE_EMAIL)
                .password("encoded").role(Role.USER).build();
        when(userRepository.findByEmail(ALICE_EMAIL)).thenReturn(Optional.of(user));
    }

    private Notification notification(Long id, String title, NotificationType type,
                                      boolean read, LocalDateTime createdAt) {
        Notification n = new Notification();
        n.setId(id);
        n.setUser(user);
        n.setTitle(title);
        n.setMessage("Message for " + title);
        n.setType(type);
        n.setRead(read);
        n.setCreatedAt(createdAt);
        return n;
    }

    @Test
    void createNotification_savesWithServerDefaults() {
        Notification saved = notification(10L, "Task Due Today", NotificationType.TASK_DUE,
                false, LocalDateTime.of(2026, 9, 1, 9, 0));
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationDto dto = notificationService.createNotification(
                ALICE_EMAIL, "Task Due Today", "Your assignment is due today.", NotificationType.TASK_DUE);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.title()).isEqualTo("Task Due Today");
        assertThat(dto.message()).isEqualTo("Message for Task Due Today");
        assertThat(dto.type()).isEqualTo(NotificationType.TASK_DUE);
        assertThat(dto.read()).isFalse();
        assertThat(dto.createdAt()).isNotNull();
        // The DTO must not expose the owner or any auth data.
        List<String> components = Arrays.stream(NotificationDto.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(components).containsExactlyInAnyOrder("id", "title", "message", "type", "read", "createdAt");
    }

    @Test
    void createNotification_unknownUser_yieldsNull() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        NotificationDto dto = notificationService.createNotification(
                "ghost@example.com", "Title", "Message", NotificationType.SYSTEM);

        assertThat(dto).isNull();
    }

    @Test
    void getNotifications_returnsNewestFirstForCurrentUser() {
        Notification a1 = notification(1L, "A1", NotificationType.SYSTEM, false,
                LocalDateTime.of(2026, 9, 1, 8, 0));
        Notification a2 = notification(2L, "A2", NotificationType.EXAM_UPCOMING, true,
                LocalDateTime.of(2026, 9, 2, 8, 0));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(List.of(a2, a1));

        List<NotificationDto> dtos = notificationService.getNotifications(ALICE_EMAIL);

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(NotificationDto::title)
                .containsExactly("A2", "A1");
    }

    @Test
    void getNotifications_unknownUser_yieldsEmptyList() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.getNotifications("ghost@example.com")).isEmpty();
        verify(notificationRepository, org.mockito.Mockito.never())
                .findByUserOrderByCreatedAtDesc(any(User.class));
    }

    @Test
    void getUnreadNotifications_returnsOnlyUnreadForCurrentUser() {
        Notification unread = notification(1L, "Unread", NotificationType.TASK_OVERDUE, false,
                LocalDateTime.of(2026, 9, 1, 9, 0));
        when(notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(unread));

        List<NotificationDto> dtos = notificationService.getUnreadNotifications(ALICE_EMAIL);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).title()).isEqualTo("Unread");
        assertThat(dtos.get(0).read()).isFalse();
    }

    @Test
    void getUnreadNotifications_unknownUser_yieldsEmptyList() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.getUnreadNotifications("ghost@example.com")).isEmpty();
        verify(notificationRepository, org.mockito.Mockito.never())
                .findByUserAndReadFalseOrderByCreatedAtDesc(any(User.class));
    }

    @Test
    void nullOrBlankEmail_yieldsEmptyResults_andNoRepositoryCall() {
        assertThat(notificationService.getNotifications(null)).isEmpty();
        assertThat(notificationService.getNotifications("  ")).isEmpty();
        assertThat(notificationService.getUnreadNotifications(null)).isEmpty();
        assertThat(notificationService.createNotification(null, "t", "m", NotificationType.SYSTEM)).isNull();
        verify(notificationRepository, org.mockito.Mockito.never())
                .findByUserOrderByCreatedAtDesc(any(User.class));
        verify(notificationRepository, org.mockito.Mockito.never())
                .findByUserAndReadFalseOrderByCreatedAtDesc(any(User.class));
    }

    // ------------------------------------------------------------------
    // Sub-Phase C additions
    // ------------------------------------------------------------------

    @Test
    void getUnreadCount_returnsCurrentUsersCount() {
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(3L);
        assertThat(notificationService.getUnreadCount(ALICE_EMAIL)).isEqualTo(3L);
    }

    @Test
    void getUnreadCount_unknownUser_returnsZero_withoutRepositoryCall() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.getUnreadCount("ghost@example.com")).isZero();
        verify(notificationRepository, org.mockito.Mockito.never())
                .countByUserAndReadFalse(any(User.class));
    }

    @Test
    void getRecentNotifications_limitedToCurrentUser() {
        Notification n1 = notification(1L, "Latest", NotificationType.TASK_DUE, false,
                LocalDateTime.of(2026, 9, 1, 9, 0));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(n1));

        List<NotificationDto> dtos = notificationService.getRecentNotifications(ALICE_EMAIL, 5);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).title()).isEqualTo("Latest");
    }

    @Test
    void getRecentNotifications_nonPositiveLimit_orUnknownUser_returnsEmpty() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.getRecentNotifications("ghost@example.com", 5)).isEmpty();
        assertThat(notificationService.getRecentNotifications(ALICE_EMAIL, 0)).isEmpty();
        assertThat(notificationService.getRecentNotifications(ALICE_EMAIL, -1)).isEmpty();
        verify(notificationRepository, org.mockito.Mockito.never())
                .findByUserOrderByCreatedAtDesc(any(User.class),
                        org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void markAsRead_setsRead_whenOwned() {
        Notification owned = notification(7L, "T", NotificationType.SYSTEM, false,
                LocalDateTime.of(2026, 9, 1, 9, 0));
        when(notificationRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(owned));

        boolean result = notificationService.markAsRead(ALICE_EMAIL, 7L);

        assertThat(result).isTrue();
        assertThat(owned.getRead()).isTrue();
        verify(notificationRepository).save(owned);
    }

    @Test
    void markAsRead_returnsFalse_whenNotOwnedOrUnknown() {
        when(notificationRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());
        assertThat(notificationService.markAsRead(ALICE_EMAIL, 999L)).isFalse();
    }

    @Test
    void markAsRead_unknownUser_returnsFalse() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.markAsRead("ghost@example.com", 1L)).isFalse();
    }

    @Test
    void markAllAsRead_returnsAffectedCount() {
        when(notificationRepository.markAllReadByUser(user)).thenReturn(4);
        assertThat(notificationService.markAllAsRead(ALICE_EMAIL)).isEqualTo(4);
    }

    @Test
    void markAllAsRead_unknownUser_returnsZero_withoutRepositoryCall() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThat(notificationService.markAllAsRead("ghost@example.com")).isZero();
        verify(notificationRepository, org.mockito.Mockito.never())
                .markAllReadByUser(any(User.class));
    }
}
