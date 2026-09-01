package com.studyflow.studyflow.scheduler;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationGenerationService;
import com.studyflow.studyflow.service.NotificationRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationScheduler}: the scheduler must fan the
 * daily run out to the generation service for every enabled user, never for
 * disabled users, must aggregate across users and must survive a per-user
 * failure without aborting the remaining users. The retention maintenance
 * trigger must delegate to the retention service with the configured days.
 */
@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationGenerationService generationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRetentionService retentionService;

    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(generationService, userRepository, retentionService, 90);
    }

    private User user(Long id, String email, boolean enabled) {
        return User.builder().id(id).name(email).email(email)
                .password("encoded").role(Role.USER).enabled(enabled).build();
    }

    @Test
    void generateDaily_forEveryEnabledUser_neverDisabledUsers() {
        when(userRepository.findByEnabledTrue())
                .thenReturn(List.of(user(1L, "a@example.com", true),
                        user(2L, "b@example.com", true)));
        when(generationService.generateNotificationsForUser("a@example.com")).thenReturn(2);
        when(generationService.generateNotificationsForUser("b@example.com")).thenReturn(3);

        scheduler.generateDailyNotifications();

        verify(generationService).generateNotificationsForUser("a@example.com");
        verify(generationService).generateNotificationsForUser("b@example.com");
        // Disabled users are never returned by findByEnabledTrue, so never processed.
        verifyNoMoreInteractions(generationService);
    }

    @Test
    void generateDaily_noEnabledUsers_noGenerationRuns() {
        when(userRepository.findByEnabledTrue()).thenReturn(List.of());

        scheduler.generateDailyNotifications();

        verify(generationService, times(0))
                .generateNotificationsForUser(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void generateDaily_aggregatesAcrossAllUsers() {
        when(userRepository.findByEnabledTrue())
                .thenReturn(List.of(user(1L, "a@example.com", true),
                        user(2L, "b@example.com", true),
                        user(3L, "c@example.com", true)));
        when(generationService.generateNotificationsForUser("a@example.com")).thenReturn(2);
        when(generationService.generateNotificationsForUser("b@example.com")).thenReturn(3);
        when(generationService.generateNotificationsForUser("c@example.com")).thenReturn(0);

        assertThatCode(() -> scheduler.generateDailyNotifications()).doesNotThrowAnyException();

        verify(generationService).generateNotificationsForUser("a@example.com");
        verify(generationService).generateNotificationsForUser("b@example.com");
        verify(generationService).generateNotificationsForUser("c@example.com");
    }

    @Test
    void generateDaily_oneUserFails_remainingUsersStillProcessed() {
        when(userRepository.findByEnabledTrue())
                .thenReturn(List.of(user(1L, "broken@example.com", true),
                        user(2L, "ok@example.com", true)));
        doThrow(new IllegalStateException("bad legacy data"))
                .when(generationService).generateNotificationsForUser("broken@example.com");
        when(generationService.generateNotificationsForUser("ok@example.com")).thenReturn(1);

        // Must not throw: the failure is isolated to the affected user.
        assertThatCode(() -> scheduler.generateDailyNotifications()).doesNotThrowAnyException();

        verify(generationService).generateNotificationsForUser("broken@example.com");
        verify(generationService).generateNotificationsForUser("ok@example.com");
    }

    @Test
    void cleanupExpired_delegatesWithConfiguredDays() {
        when(retentionService.cleanupReadOlderThan(90)).thenReturn(7);

        scheduler.cleanupExpiredNotifications();

        verify(retentionService).cleanupReadOlderThan(90);
    }
}
