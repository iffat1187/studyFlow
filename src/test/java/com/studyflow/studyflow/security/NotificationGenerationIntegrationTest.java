package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.NotificationDto;
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
import com.studyflow.studyflow.service.NotificationGenerationService;
import com.studyflow.studyflow.service.NotificationService;
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
 * Sub-Phase B integration tests: every notification generation rule runs
 * against the real database (no stubs) using the actual user-scoped queries
 * the service uses at runtime. The "today/tomorrow/yesterday" relationships
 * are derived from {@link LocalDate#now()} so the rules match real wall-clock
 * dates; records are seeded inside a transaction and rolled back.
 *
 * Covers the Task Due, Task Overdue, Exam Upcoming, Exam Today, Study
 * Reminder and Streak Reminder rules, duplicate/idempotency prevention,
 * user isolation, the no-false-positives guardrails, and the persisted-field
 * contract (user_id, title, message, type, read=false, created_at).
 */
@SpringBootTest
@Transactional
class NotificationGenerationIntegrationTest {

    private static final String ALICE_EMAIL = "g-alice@example.com";
    private static final String BOB_EMAIL = "g-bob@example.com";

    @Autowired
    private NotificationGenerationService generationService;

    @Autowired
    private NotificationService notificationService;

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

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private Subject saveSubject(User user, String name) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor("Blue");
        subject.setUser(user);
        return subjectRepository.save(subject);
    }

    private Task saveTask(Subject subject, String title, LocalDate due, LocalTime time, boolean completed) {
        Task task = new Task();
        task.setTitle(title);
        task.setType(TaskType.ASSIGNMENT);
        task.setDueDate(due);
        task.setDueTime(time);
        task.setCompleted(completed);
        task.setSubject(subject);
        return taskRepository.save(task);
    }

    private Exam saveExam(Subject subject, String name, LocalDate date, LocalTime time) {
        Exam exam = new Exam();
        exam.setName(name);
        exam.setExamType(ExamType.MIDTERM);
        exam.setExamDate(date);
        exam.setExamTime(time);
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

    // ------------------------------------------------------------------
    // TASK DUE (spec 35)
    // ------------------------------------------------------------------

    @Test
    void taskDue_incompleteTaskToday_createsOnce_notDuplicate() {
        User alice = saveUser(ALICE_EMAIL);
        saveTask(saveSubject(alice, "Math"), "JPA Assignment", LocalDate.now(), LocalTime.of(20, 0), false);
        saveSession(saveSubject(alice, "Math"), LocalDate.now()); // studied today -> no reminders

        int first = generationService.generateNotificationsForUser(ALICE_EMAIL);
        int second = generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(1, first, "one TASK_DUE created");
        assertEquals(0, second, "no duplicate on re-run");
        assertEquals(1, countFor(alice, NotificationType.TASK_DUE));

        List<Notification> tasks = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        assertEquals(NotificationType.TASK_DUE, tasks.get(0).getType());
        assertEquals("\"JPA Assignment\" is due today.", tasks.get(0).getMessage());
        assertFalse(tasks.get(0).getRead());
    }

    @Test
    void taskDue_completedTaskToday_noNotification() {
        User alice = saveUser(ALICE_EMAIL);
        saveTask(saveSubject(alice, "Math"), "Done Task", LocalDate.now(), LocalTime.of(20, 0), true);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.TASK_DUE));
    }

    // ------------------------------------------------------------------
    // TASK OVERDUE (spec 36)
    // ------------------------------------------------------------------

    @Test
    void taskOverdue_incompletePastTask_createsOnce_notDuplicate_fullContract() {
        User alice = saveUser(ALICE_EMAIL);
        saveTask(saveSubject(alice, "Sql"), "Database Assignment", LocalDate.now().minusDays(1), null, false);
        saveSession(saveSubject(alice, "Sql"), LocalDate.now());

        int first = generationService.generateNotificationsForUser(ALICE_EMAIL);
        int second = generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countFor(alice, NotificationType.TASK_OVERDUE));

        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        Notification n = list.get(0);
        assertEquals(NotificationType.TASK_OVERDUE, n.getType());
        assertEquals("\"Database Assignment\" is overdue.", n.getMessage());
        assertEquals(alice.getId(), n.getUser().getId());
        assertTrue(n.getCreatedAt() != null);
    }

    @Test
    void taskOverdue_completedPastTask_noNotification() {
        User alice = saveUser(ALICE_EMAIL);
        saveTask(saveSubject(alice, "Sql"), "Done Past", LocalDate.now().minusDays(1), null, true);
        saveSession(saveSubject(alice, "Sql"), LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.TASK_OVERDUE));
        assertEquals(0, countFor(alice, NotificationType.TASK_DUE));
    }

    // ------------------------------------------------------------------
    // EXAM TOMORROW (spec 37)
    // ------------------------------------------------------------------

    @Test
    void examUpcoming_examTomorrow_createsOnce_noDuplicate() {
        User alice = saveUser(ALICE_EMAIL);
        saveExam(saveSubject(alice, "Spring"), "Spring Boot Final", LocalDate.now().plusDays(1), LocalTime.of(10, 0));
        saveSession(saveSubject(alice, "Spring"), LocalDate.now());

        int first = generationService.generateNotificationsForUser(ALICE_EMAIL);
        int second = generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countFor(alice, NotificationType.EXAM_UPCOMING));
        assertEquals("\"Spring Boot Final\" is tomorrow at 10:00 AM.",
                notificationRepository.findByUserOrderByCreatedAtDesc(alice).get(0).getMessage());
    }

    @Test
    void examUpcoming_examTomorrowWithoutTime_messageOmitsTime() {
        User alice = saveUser(ALICE_EMAIL);
        saveExam(saveSubject(alice, "Spring"), "No Time Exam", LocalDate.now().plusDays(1), null);
        saveSession(saveSubject(alice, "Spring"), LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        String msg = notificationRepository.findByUserOrderByCreatedAtDesc(alice).get(0).getMessage();
        assertEquals("\"No Time Exam\" is tomorrow.", msg);
        assertFalse(msg.contains(" at "));
    }

    // ------------------------------------------------------------------
    // EXAM TODAY (spec 38)
    // ------------------------------------------------------------------

    @Test
    void examToday_examToday_createsExamTodayOnly_notUpcoming() {
        User alice = saveUser(ALICE_EMAIL);
        saveExam(saveSubject(alice, "Db"), "Database Final", LocalDate.now(), LocalTime.of(10, 0));
        saveSession(saveSubject(alice, "Db"), LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(1, countFor(alice, NotificationType.EXAM_TODAY));
        assertEquals(0, countFor(alice, NotificationType.EXAM_UPCOMING));
        assertEquals("\"Database Final\" is today at 10:00 AM.",
                notificationRepository.findByUserOrderByCreatedAtDesc(alice).get(0).getMessage());
    }

    // ------------------------------------------------------------------
    // STUDY REMINDER (spec 39)
    // ------------------------------------------------------------------

    @Test
    void studyReminder_noSessionToday_createsOnce_noDuplicate() {
        User alice = saveUser(ALICE_EMAIL);
        saveSubject(alice, "Math");

        int first = generationService.generateNotificationsForUser(ALICE_EMAIL);
        int second = generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countFor(alice, NotificationType.STUDY_REMINDER));
    }

    @Test
    void studyReminder_sessionToday_noReminder() {
        User alice = saveUser(ALICE_EMAIL);
        saveSession(saveSubject(alice, "Math"), LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.STUDY_REMINDER));
    }

    // ------------------------------------------------------------------
    // STREAK REMINDER (spec 40 + 13)
    // ------------------------------------------------------------------

    @Test
    void streakReminder_streakAtRisk_createsOnce_messageHasRealStreak() {
        User alice = saveUser(ALICE_EMAIL);
        Subject math = saveSubject(alice, "Math");
        // Consecutive active days ending yesterday, none today.
        saveSession(math, LocalDate.now().minusDays(1));
        saveSession(math, LocalDate.now().minusDays(2));
        saveSession(math, LocalDate.now().minusDays(3));

        int first = generationService.generateNotificationsForUser(ALICE_EMAIL);
        int second = generationService.generateNotificationsForUser(ALICE_EMAIL);

        // first run: STREAK_REMINDER + STUDY_REMINDER (not studied today) = 2 new.
        assertEquals(2, first);
        assertEquals(0, second);
        assertEquals(1, countFor(alice, NotificationType.STREAK_REMINDER));
        Notification streak = notificationRepository.findByUserOrderByCreatedAtDesc(alice).stream()
                .filter(n -> n.getType() == NotificationType.STREAK_REMINDER)
                .findFirst().orElseThrow();
        assertEquals("Study today to keep your 3-day study streak going.", streak.getMessage());
    }

    @Test
    void streakReminder_sessionToday_noStreakReminder() {
        User alice = saveUser(ALICE_EMAIL);
        Subject math = saveSubject(alice, "Math");
        saveSession(math, LocalDate.now().minusDays(1));
        saveSession(math, LocalDate.now().minusDays(2));
        saveSession(math, LocalDate.now()); // studied today

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.STREAK_REMINDER));
        assertEquals(0, countFor(alice, NotificationType.STUDY_REMINDER));
    }

    @Test
    void streakReminder_noPreviousStreak_noReminder() {
        User alice = saveUser(ALICE_EMAIL);
        saveSubject(alice, "Math"); // no sessions at all

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.STREAK_REMINDER));
    }

    // ------------------------------------------------------------------
    // USER ISOLATION (spec 41)
    // ------------------------------------------------------------------

    @Test
    void userIsolation_generationScopedToCurrentUser() {
        User alice = saveUser(ALICE_EMAIL);
        User bob = saveUser(BOB_EMAIL);
        saveTask(saveSubject(alice, "A"), "Alice Task", LocalDate.now(), null, false);
        saveTask(saveSubject(bob, "B"), "Bob Task", LocalDate.now(), null, false);
        saveExam(saveSubject(alice, "A"), "Alice Exam", LocalDate.now(), null);

        generationService.generateNotificationsForUser(ALICE_EMAIL);
        generationService.generateNotificationsForUser(BOB_EMAIL);

        // Alice: TASK_DUE (Alice Task) + EXAM_TODAY (Alice Exam) + STUDY_REMINDER.
        List<Notification> aliceList =
                notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        List<Notification> bobList = notificationRepository.findByUserOrderByCreatedAtDesc(bob);

        assertTrue(aliceList.stream().anyMatch(n ->
                n.getType() == NotificationType.TASK_DUE && n.getMessage().contains("Alice Task")));
        assertFalse(aliceList.stream().anyMatch(n -> n.getMessage().contains("Bob Task")));
        assertTrue(aliceList.stream().anyMatch(n -> n.getType() == NotificationType.EXAM_TODAY));
        assertTrue(aliceList.stream().anyMatch(n -> n.getType() == NotificationType.STUDY_REMINDER));

        // Bob sees only Bob's task due + study reminder, never Alice's data.
        assertTrue(bobList.stream().anyMatch(n ->
                n.getType() == NotificationType.TASK_DUE && n.getMessage().contains("Bob Task")));
        assertFalse(bobList.stream().anyMatch(n -> n.getMessage().contains("Alice")));
        assertEquals(1, bobList.stream().filter(n -> n.getType() == NotificationType.TASK_DUE).count());
    }

    // ------------------------------------------------------------------
    // MULTIPLE RULES (spec 42)
    // ------------------------------------------------------------------

    @Test
    void multipleRules_allExpectedTypes_noDuplicates() {
        User alice = saveUser(ALICE_EMAIL);
        Subject math = saveSubject(alice, "Math");
        saveTask(math, "Due Today", LocalDate.now(), null, false);
        saveTask(math, "Overdue", LocalDate.now().minusDays(2), null, false);
        saveExam(math, "Exam Tmrw", LocalDate.now().plusDays(1), null);
        // No session today, but a prior streak -> streak reminder fires too.
        saveSession(math, LocalDate.now().minusDays(1));
        saveSession(math, LocalDate.now().minusDays(2));

        int created = generationService.generateNotificationsForUser(ALICE_EMAIL);

        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        assertTrue(list.stream().anyMatch(n -> n.getType() == NotificationType.TASK_DUE));
        assertTrue(list.stream().anyMatch(n -> n.getType() == NotificationType.TASK_OVERDUE));
        assertTrue(list.stream().anyMatch(n -> n.getType() == NotificationType.EXAM_UPCOMING));
        assertTrue(list.stream().anyMatch(n -> n.getType() == NotificationType.STUDY_REMINDER));
        assertTrue(list.stream().anyMatch(n -> n.getType() == NotificationType.STREAK_REMINDER));
        assertTrue(created >= 5);
    }

    // ------------------------------------------------------------------
    // NO FALSE POSITIVES (spec 43)
    // ------------------------------------------------------------------

    @Test
    void noFalsePositives_pastExam_studiedNoReminders() {
        User alice = saveUser(ALICE_EMAIL);
        Subject math = saveSubject(alice, "Math");
        saveExam(math, "Past Exam", LocalDate.now().minusDays(3), null);
        saveSession(math, LocalDate.now());

        generationService.generateNotificationsForUser(ALICE_EMAIL);

        assertEquals(0, countFor(alice, NotificationType.EXAM_TODAY));
        assertEquals(0, countFor(alice, NotificationType.EXAM_UPCOMING));
        assertEquals(0, countFor(alice, NotificationType.STUDY_REMINDER));
        assertEquals(0, countFor(alice, NotificationType.STREAK_REMINDER));
    }

    // ------------------------------------------------------------------
    // SOURCE KEY + IDEMPOTENCY CONTRACT (spec 44)
    // ------------------------------------------------------------------

    @Test
    void persistedRecords_contractAndDedupKeyset() {
        User alice = saveUser(ALICE_EMAIL);
        saveTask(saveSubject(alice, "Math"), "Once", LocalDate.now(), null, false);

        generationService.generateNotificationsForUser(ALICE_EMAIL);
        generationService.generateNotificationsForUser(ALICE_EMAIL);
        generationService.generateNotificationsForUser(ALICE_EMAIL);

        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        // The single TASK_DUE record must have exactly one stable source key.
        List<String> keys = list.stream()
                .filter(n -> n.getType() == NotificationType.TASK_DUE)
                .map(Notification::getSourceKey)
                .toList();
        assertEquals(1, keys.size());
        assertTrue(keys.get(0).matches("TASK_DUE:\\d+"));
    }

    @Test
    void notificationService_carriesSourceKey_forDedup() {
        User alice = saveUser(ALICE_EMAIL);
        NotificationDto a = notificationService.createNotification(
                ALICE_EMAIL, "T", "M", NotificationType.SYSTEM, "SYSTEM:1");
        NotificationDto b = notificationService.createNotification(
                ALICE_EMAIL, "T", "M", NotificationType.SYSTEM, "SYSTEM:1");
        assertTrue(a.id() != null);
        assertTrue(b.id() != null);
        // Different ids because it is a plain create; dedup is the generator's job.
        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        assertEquals(2, list.stream().filter(n -> "SYSTEM:1".equals(n.getSourceKey())).count());
    }
}
