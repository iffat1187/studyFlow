package com.studyflow.studyflow.service;

import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.NotificationGenerationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase B service-level tests for the generation rules.
 *
 * Mocks the repositories and the {@link NotificationService} so the rule
 * decisions (which event fires, idempotency via the existence check, and
 * scoping to the authenticated user) can be verified in isolation. The real
 * SQL for each query is exercised against the live database in
 * {@code NotificationGenerationIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationGenerationServiceTest {

    private static final String ALICE_EMAIL = "alice@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationPreferenceService preferenceService;

    @InjectMocks
    private NotificationGenerationServiceImpl generationService;

    private User user;
    private Subject subject;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        user = User.builder().id(1L).name("Alice").email(ALICE_EMAIL)
                .password("encoded").role(Role.USER).build();
        subject = new Subject();
        subject.setId(1L);
        subject.setName("Math");
        subject.setUser(user);
        when(userRepository.findByEmail(ALICE_EMAIL)).thenReturn(Optional.of(user));
        // By default: no tasks/exams, but "studied today" so the daily
        // reminders (which would inflate per-rule counts) do NOT fire.
        when(taskRepository.findIncompleteDueTodayByUser(user, today)).thenReturn(List.of());
        when(taskRepository.findIncompleteOverdueByUser(user, today)).thenReturn(List.of());
        when(examRepository.findUpcomingFromByUser(user, today)).thenReturn(List.of());
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user)).thenReturn(List.of(today));
        // By default every category is enabled so per-rule behaviour is tested;
        // dedicated "disabled category" tests override individual toggles.
        when(preferenceService.isEnabled(eq(user), any(NotificationType.class))).thenReturn(true);
    }

    private Task task(long id, String title, LocalDate due) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setType(TaskType.ASSIGNMENT);
        t.setDueDate(due);
        t.setCompleted(false);
        t.setSubject(subject);
        return t;
    }

    private Exam exam(long id, String name, LocalDate date) {
        Exam e = new Exam();
        e.setId(id);
        e.setName(name);
        e.setExamType(ExamType.MIDTERM);
        e.setExamDate(date);
        e.setDurationMinutes(120);
        e.setSubject(subject);
        return e;
    }

    @Test
    void taskDue_createsWhenNotExists_skipsWhenExists() {
        when(taskRepository.findIncompleteDueTodayByUser(user, today)).thenReturn(List.of(task(15L, "JPA", today)));
        when(notificationRepository.existsByUserAndSourceKey(user, "TASK_DUE:15")).thenReturn(false, true);

        assertEquals(1, generationService.generateNotificationsForUser(ALICE_EMAIL));
        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, times(1))
                .createNotification(anyString(), anyString(), anyString(),
                        any(NotificationType.class), anyString());
    }

    @Test
    void completedTask_neverFires() {
        // Not needed: the repository query already excludes completed tasks,
        // but this guards the contract that createNotification is not called
        // for a completed task (here: no due-today rows returned).
        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));
        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        any(NotificationType.class), any());
    }

    @Test
    void streakAtRisk_usesRealConsecutiveRun_andIdempotent() {
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2), today.minusDays(3)));
        when(notificationRepository.existsByUserAndSourceKey(user, "STREAK_REMINDER:" + today))
                .thenReturn(false, true);

        // First run: streak (3) + study reminder both "missing".
        when(notificationRepository.existsByUserAndSourceKey(user, "STUDY_REMINDER:" + today))
                .thenReturn(false, true);

        assertEquals(2, generationService.generateNotificationsForUser(ALICE_EMAIL));
        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, times(1))
                .createNotification(ALICE_EMAIL,
                        "Keep Your Streak",
                        "Study today to keep your 3-day study streak going.",
                        NotificationType.STREAK_REMINDER, "STREAK_REMINDER:" + today);
    }

    @Test
    void studiedToday_skipsBothReminders() {
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user))
                .thenReturn(List.of(today.minusDays(1), today));

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STREAK_REMINDER), any());
        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STUDY_REMINDER), any());
    }

    @Test
    void unknownUser_generatesNothing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertEquals(0, generationService.generateNotificationsForUser("ghost@example.com"));

        verify(taskRepository, never()).findIncompleteDueTodayByUser(any(), any());
        verify(examRepository, never()).findUpcomingFromByUser(any(), any());
    }

    // ---------- Preference gating (Sub-Phase E) ----------

    @Test
    void taskDue_disabled_skipsTaskDueButKeepsOtherRules() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.TASK_DUE))).thenReturn(false);
        when(taskRepository.findIncompleteDueTodayByUser(user, today)).thenReturn(List.of(task(15L, "JPA", today)));

        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.TASK_DUE), any());
    }

    @Test
    void taskOverdue_disabled_skips() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.TASK_OVERDUE))).thenReturn(false);
        when(taskRepository.findIncompleteOverdueByUser(user, today))
                .thenReturn(List.of(task(16L, "Essay", today.minusDays(1))));

        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.TASK_OVERDUE), any());
    }

    @Test
    void examRules_disabled_skips() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.EXAM_TODAY))).thenReturn(false);
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.EXAM_UPCOMING))).thenReturn(false);
        when(examRepository.findUpcomingFromByUser(user, today))
                .thenReturn(List.of(exam(20L, "Math Midterm", today)));

        assertEquals(0, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.EXAM_TODAY), any());
    }

    @Test
    void studyReminder_disabled_skipsOnlyStudyReminder() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.STUDY_REMINDER))).thenReturn(false);
        // Consecutive active days ending yesterday -> the streak is at risk
        // and (since only STUDY_REMINDER is disabled) the streak reminder fires.
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2)));
        when(notificationRepository.existsByUserAndSourceKey(user, "STREAK_REMINDER:" + today)).thenReturn(false);

        assertEquals(1, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STUDY_REMINDER), any());
        verify(notificationService, times(1))
                .createNotification(anyString(), eq("Keep Your Streak"), anyString(),
                        eq(NotificationType.STREAK_REMINDER), eq("STREAK_REMINDER:" + today));
    }

    @Test
    void streakReminder_disabled_skipsOnlyStreak() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.STREAK_REMINDER))).thenReturn(false);
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2)));
        when(notificationRepository.existsByUserAndSourceKey(user, "STUDY_REMINDER:" + today)).thenReturn(false);

        assertEquals(1, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STREAK_REMINDER), any());
        verify(notificationService, times(1))
                .createNotification(anyString(), eq("Study Reminder"), anyString(),
                        eq(NotificationType.STUDY_REMINDER), eq("STUDY_REMINDER:" + today));
    }

    @Test
    void allTaskRulesDisabled_otherCategoriesStillEmitted() {
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.TASK_DUE))).thenReturn(false);
        when(preferenceService.isEnabled(eq(user), eq(NotificationType.TASK_OVERDUE))).thenReturn(false);
        // At-risk streak + not studied today -> BOTH study and streak reminders fire.
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(user))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2)));
        when(notificationRepository.existsByUserAndSourceKey(user, "STREAK_REMINDER:" + today)).thenReturn(false);
        when(notificationRepository.existsByUserAndSourceKey(user, "STUDY_REMINDER:" + today)).thenReturn(false);

        assertEquals(2, generationService.generateNotificationsForUser(ALICE_EMAIL));

        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.TASK_DUE), any());
        verify(notificationService, never())
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.TASK_OVERDUE), any());
        verify(notificationService, times(1))
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STUDY_REMINDER), any());
        verify(notificationService, times(1))
                .createNotification(anyString(), anyString(), anyString(),
                        eq(NotificationType.STREAK_REMINDER), any());
    }
}
