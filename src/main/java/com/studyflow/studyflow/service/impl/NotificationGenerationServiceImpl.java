package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationGenerationService;
import com.studyflow.studyflow.service.NotificationPreferenceService;
import com.studyflow.studyflow.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Implementation of {@link NotificationGenerationService}.
 *
 * Each rule evaluates the user's real database data, resolves to a
 * deterministic {@code sourceKey}, and only persists when no notification
 * for that event already exists (idempotent generation). Ownership is
 * enforced by resolving the {@link User} from the authenticated email and
 * by using only user-scoped repository queries — never global {@code
 * findAll()} — so a rule can never generate a notification for another
 * user.
 *
 * Date handling uses {@link LocalDate}/{@link LocalTime} consistent with the
 * rest of the application ({@code LocalDate.now()} in the server's
 * timezone), so no UTC shifts accidentally move a task/exam day.
 *
 * Before creating a notification, each rule consults the user's
 * {@link com.studyflow.studyflow.service.NotificationPreferenceService}
 * preferences for that category. Disabled categories are skipped entirely;
 * a user with no preference record defaults to every category enabled, so a
 * fresh account still receives notifications out of the box.
 *
 * The Study/Streak reminder logic reuses the application's real streak
 * foundation: the distinct active dates (sessions with
 * {@code durationMinutes > 0}) from {@link StudySessionRepository}, and the
 * same walk-backward consecutive-day algorithm the analytics module uses for
 * the current streak.
 */
@Service
public class NotificationGenerationServiceImpl implements NotificationGenerationService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ExamRepository examRepository;
    private final StudySessionRepository studySessionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    /**
     * Constructor injection.
     */
    public NotificationGenerationServiceImpl(UserRepository userRepository,
                                             TaskRepository taskRepository,
                                             ExamRepository examRepository,
                                             StudySessionRepository studySessionRepository,
                                             NotificationRepository notificationRepository,
                                             NotificationService notificationService,
                                             NotificationPreferenceService preferenceService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.examRepository = examRepository;
        this.studySessionRepository = studySessionRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
    }

    @Override
    @Transactional
    public int generateNotificationsForUser(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int created = 0;

        // 1 + 2. Task rules (incomplete only; completed tasks never notify).
        if (preferenceService.isEnabled(user, NotificationType.TASK_DUE)) {
            for (Task task : taskRepository.findIncompleteDueTodayByUser(user, today)) {
                created += createIfMissing(
                        user, NotificationType.TASK_DUE, "Task Due Today",
                        "\"" + task.getTitle() + "\" is due today.", key("TASK_DUE", task.getId()));
            }
        }
        if (preferenceService.isEnabled(user, NotificationType.TASK_OVERDUE)) {
            for (Task task : taskRepository.findIncompleteOverdueByUser(user, today)) {
                created += createIfMissing(
                        user, NotificationType.TASK_OVERDUE, "Task Overdue",
                        "\"" + task.getTitle() + "\" is overdue.", key("TASK_OVERDUE", task.getId()));
            }
        }

        // 3 + 4. Exam rules (only upcoming exams; past exams never notify).
        LocalDate tomorrow = today.plusDays(1);
        for (Exam exam : examRepository.findUpcomingFromByUser(user, today)) {
            if (exam.getExamDate().equals(today)) {
                if (preferenceService.isEnabled(user, NotificationType.EXAM_TODAY)) {
                    created += createIfMissing(
                            user, NotificationType.EXAM_TODAY, "Exam Today",
                            examTodayMessage(exam), key("EXAM_TODAY", exam.getId()));
                }
            } else if (exam.getExamDate().equals(tomorrow)) {
                if (preferenceService.isEnabled(user, NotificationType.EXAM_UPCOMING)) {
                    created += createIfMissing(
                            user, NotificationType.EXAM_UPCOMING, "Exam Tomorrow",
                            examTomorrowMessage(exam), key("EXAM_UPCOMING", exam.getId()));
                }
            }
        }

        // The study/streak reminders both hinge on whether the user studied today.
        Set<LocalDate> activeDates = new HashSet<>(
                studySessionRepository.findDistinctActiveDatesBySubject_User(user));
        boolean studiedToday = activeDates.contains(today);

        // 5. Study reminder (at most one per day).
        if (!studiedToday && preferenceService.isEnabled(user, NotificationType.STUDY_REMINDER)) {
            created += createIfMissing(
                    user, NotificationType.STUDY_REMINDER, "Study Reminder",
                    "You haven't recorded a study session today. Start a session to keep your study plan on track.",
                    key("STUDY_REMINDER", today));
        }

        // 6. Streak reminder (at most one per day, only when a streak is at risk).
        if (!studiedToday && preferenceService.isEnabled(user, NotificationType.STREAK_REMINDER)) {
            int atRiskStreak = consecutiveActiveDaysEndingAt(today.minusDays(1), activeDates);
            if (atRiskStreak >= 1) {
                created += createIfMissing(
                        user, NotificationType.STREAK_REMINDER, "Keep Your Streak",
                        "Study today to keep your " + atRiskStreak + "-day study streak going.",
                        key("STREAK_REMINDER", today));
            }
        }

        return created;
    }

    /**
     * Persist a notification for the user when no notification with the
     * given source key already exists (idempotency). Returns 1 when created,
     * 0 when it already existed (or the user was null).
     */
    private int createIfMissing(User user, NotificationType type, String title,
                                String message, String sourceKey) {
        if (notificationRepository.existsByUserAndSourceKey(user, sourceKey)) {
            return 0;
        }
        notificationService.createNotification(user.getEmail(), title, message, type, sourceKey);
        return 1;
    }

    /**
     * "Database Final is today at 10:00 AM" or "Database Final is today."
     */
    private String examTodayMessage(Exam exam) {
        return "\"" + exam.getName() + "\" is today" + timeSuffix(exam.getExamTime()) + ".";
    }

    /**
     * "Spring Boot Final is tomorrow at 10:00 AM" or "Spring Boot Final is
     * tomorrow." A null time never invents one.
     */
    private String examTomorrowMessage(Exam exam) {
        return "\"" + exam.getName() + "\" is tomorrow" + timeSuffix(exam.getExamTime()) + ".";
    }

    /**
     * " at h:mm AM/PM" when a time is present, otherwise the empty string.
     */
    private String timeSuffix(LocalTime time) {
        if (time == null) {
            return "";
        }
        return " at " + time.format(TIME_FORMAT);
    }

    /**
     * Consecutive calendar days ending at the given date on which the user
     * has an active (positive-duration) session. This mirrors the analytics
     * module's current-streak walk-backward algorithm, evaluated from a
     * chosen end date, so the streak value reuses the same real streak rule.
     */
    private int consecutiveActiveDaysEndingAt(LocalDate end, Set<LocalDate> activeDates) {
        int count = 0;
        LocalDate day = end;
        while (activeDates.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    private String key(String rule, long id) {
        return rule + ":" + id;
    }

    private String key(String rule, LocalDate date) {
        return rule + ":" + date;
    }

    /**
     * Resolve the authenticated user record, or null when it cannot be found.
     */
    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }
}
