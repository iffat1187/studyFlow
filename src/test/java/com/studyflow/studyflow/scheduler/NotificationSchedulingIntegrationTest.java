package com.studyflow.studyflow.scheduler;

import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-Phase D integration tests: the scheduled notification job runs against
 * the real database via the actual scheduler loop, exercising every
 * Sub-Phase B rule end-to-end (all of Sub-Phase D's manual scheduler tests).
 *
 * Records are seeded inside a transaction and rolled back; "today/tomorrow/
 * yesterday" are derived from {@link LocalDate#now()} so rules match real
 * wall-clock dates. Idempotency (no duplicates across restarts / re-runs),
 * multi-user isolation, disabled-user skipping and read=false for fresh
 * notifications are all verified here.
 */
@SpringBootTest
@Transactional
class NotificationSchedulingIntegrationTest {

    private static final String ALICE = "s-alice@example.com";
    private static final String BOB = "s-bob@example.com";

    @Autowired
    private NotificationScheduler scheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email, boolean enabled) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .enabled(enabled)
                .build());
    }

    private Subject saveSubject(User user, String name) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor("Blue");
        subject.setUser(user);
        return subjectRepository.save(subject);
    }

    private Task saveTask(Subject subject, String title, LocalDate due, boolean completed) {
        Task task = new Task();
        task.setTitle(title);
        task.setType(TaskType.ASSIGNMENT);
        task.setDueDate(due);
        task.setCompleted(completed);
        task.setSubject(subject);
        return taskRepository.save(task);
    }

    private Exam saveExam(Subject subject, String name, LocalDate date) {
        Exam exam = new Exam();
        exam.setName(name);
        exam.setExamType(ExamType.MIDTERM);
        exam.setExamDate(date);
        exam.setDurationMinutes(120);
        exam.setSubject(subject);
        return examRepository.save(exam);
    }

    private StudySession saveSession(Subject subject, LocalDate date) {
        StudySession session = new StudySession();
        session.setSessionType(StudySessionType.REVISION);
        session.setSessionDate(date);
        session.setDurationMinutes(60);
        session.setSubject(subject);
        return studySessionRepository.save(session);
    }

    private long countFor(User user, NotificationType type) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(n -> n.getType() == type)
                .count();
    }

    // TEST 1 — basic execution completes without exception.
    @Test
    void scheduler_basicExecution_completesWithoutException() {
        User alice = saveUser(ALICE, true);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());
        scheduler.generateDailyNotifications();
        // no assertion beyond completing; a task/exam run below covers rules.
        assertTrue(true);
    }

    // TEST 2 — TASK_DUE created once, no duplicate on re-run.
    @Test
    void scheduler_taskDue_createdOnce_noDuplicate() {
        User alice = saveUser(ALICE, true);
        saveTask(saveSubject(alice, "Math"), "JPA Assignment", LocalDate.now(), false);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.TASK_DUE));
    }

    // TEST 3 — TASK_OVERDUE created once.
    @Test
    void scheduler_taskOverdue_createdOnce() {
        User alice = saveUser(ALICE, true);
        saveTask(saveSubject(alice, "Sql"), "Old Assignment", LocalDate.now().minusDays(2), false);
        saveSession(saveSubject(alice, "Sql"), LocalDate.now());

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.TASK_OVERDUE));
    }

    // TEST 4 — completed task generates no due/overdue notification.
    @Test
    void scheduler_completedTask_noDueOrOverdue() {
        User alice = saveUser(ALICE, true);
        saveTask(saveSubject(alice, "Math"), "Done Today", LocalDate.now(), true);
        saveTask(saveSubject(alice, "Math"), "Done Past", LocalDate.now().minusDays(2), true);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        scheduler.generateDailyNotifications();

        assertEquals(0, countFor(alice, NotificationType.TASK_DUE));
        assertEquals(0, countFor(alice, NotificationType.TASK_OVERDUE));
    }

    // TEST 5 — EXAM_UPCOMING (exam tomorrow) created once.
    @Test
    void scheduler_examTomorrow_createdOnce() {
        User alice = saveUser(ALICE, true);
        saveExam(saveSubject(alice, "Spring"), "Spring Final", LocalDate.now().plusDays(1));
        saveSession(saveSubject(alice, "Spring"), LocalDate.now());

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.EXAM_UPCOMING));
    }

    // TEST 6 — EXAM_TODAY created once (and not as upcoming).
    @Test
    void scheduler_examToday_createdOnce() {
        User alice = saveUser(ALICE, true);
        saveExam(saveSubject(alice, "Db"), "Database Final", LocalDate.now());
        saveSession(saveSubject(alice, "Db"), LocalDate.now());

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.EXAM_TODAY));
        assertEquals(0, countFor(alice, NotificationType.EXAM_UPCOMING));
    }

    // TEST 7 — STUDY_REMINDER when no session today, once, no duplicate.
    @Test
    void scheduler_studyReminder_noSessionToday_createdOnce() {
        User alice = saveUser(ALICE, true);
        saveSubject(alice, "Math");

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.STUDY_REMINDER));
    }

    // TEST 8 — studied today => no STUDY_REMINDER.
    @Test
    void scheduler_studiedToday_noStudyReminder() {
        User alice = saveUser(ALICE, true);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        scheduler.generateDailyNotifications();

        assertEquals(0, countFor(alice, NotificationType.STUDY_REMINDER));
    }

    // TEST 9 — STREAK_REMINDER when the existing streak rule says at risk.
    @Test
    void scheduler_streakAtRisk_createdOnce() {
        User alice = saveUser(ALICE, true);
        Subject math = saveSubject(alice, "Math");
        saveSession(math, LocalDate.now().minusDays(1));
        saveSession(math, LocalDate.now().minusDays(2));
        saveSession(math, LocalDate.now().minusDays(3));

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.STREAK_REMINDER));
        Notification streak = notificationRepository.findByUserOrderByCreatedAtDesc(alice).stream()
                .filter(n -> n.getType() == NotificationType.STREAK_REMINDER)
                .findFirst().orElseThrow();
        assertEquals("Study today to keep your 3-day study streak going.", streak.getMessage());
    }

    // TEST 10 — multi-user: each user only receives their own notifications.
    @Test
    void scheduler_multiUser_eachUserIsolated() {
        User alice = saveUser(ALICE, true);
        User bob = saveUser(BOB, true);
        saveTask(saveSubject(alice, "A"), "Alice Only", LocalDate.now(), false);
        saveTask(saveSubject(bob, "B"), "Bob Only", LocalDate.now(), false);
        saveExam(saveSubject(alice, "A"), "Alice Exam", LocalDate.now());

        scheduler.generateDailyNotifications();

        List<Notification> aliceList = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        List<Notification> bobList = notificationRepository.findByUserOrderByCreatedAtDesc(bob);
        assertTrue(aliceList.stream().anyMatch(n -> n.getType() == NotificationType.TASK_DUE
                && n.getMessage().contains("Alice Only")));
        assertFalse(aliceList.stream().anyMatch(n -> n.getMessage().contains("Bob Only")));
        assertTrue(aliceList.stream().anyMatch(n -> n.getType() == NotificationType.EXAM_TODAY));
        assertTrue(bobList.stream().anyMatch(n -> n.getType() == NotificationType.TASK_DUE
                && n.getMessage().contains("Bob Only")));
        assertFalse(bobList.stream().anyMatch(n -> n.getMessage().contains("Alice")));
    }

    // Restart/idempotency — a re-run never duplicates (covered above too,
    // explicit here for clarity).
    @Test
    void scheduler_runningManyTimes_neverDuplicates() {
        User alice = saveUser(ALICE, true);
        saveTask(saveSubject(alice, "Math"), "The One", LocalDate.now(), false);
        saveExam(saveSubject(alice, "Math"), "The Exam", LocalDate.now());
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();
        scheduler.generateDailyNotifications();

        assertEquals(1, countFor(alice, NotificationType.TASK_DUE));
        assertEquals(1, countFor(alice, NotificationType.EXAM_TODAY));
    }

    // Disabled users are skipped entirely.
    @Test
    void scheduler_disabledUser_skipped() {
        User disabled = saveUser(ALICE, false);
        saveSubject(disabled, "Math");

        scheduler.generateDailyNotifications();

        assertEquals(0, notificationRepository.findByUserOrderByCreatedAtDesc(disabled).size());
    }

    // Freshly generated notifications are unread.
    @Test
    void scheduler_newNotifications_areUnread() {
        User alice = saveUser(ALICE, true);
        saveTask(saveSubject(alice, "Math"), "Due", LocalDate.now(), false);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        scheduler.generateDailyNotifications();

        Notification n = notificationRepository.findByUserOrderByCreatedAtDesc(alice)
                .stream().filter(x -> x.getType() == NotificationType.TASK_DUE).findFirst().orElseThrow();
        assertFalse(n.getRead(), "freshly generated notification must be unread");
    }
}
