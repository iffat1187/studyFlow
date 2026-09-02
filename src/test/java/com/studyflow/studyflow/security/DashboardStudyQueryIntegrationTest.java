package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.DashboardData;
import com.studyflow.studyflow.dto.DashboardSubjectTopics;
import com.studyflow.studyflow.dto.DashboardTaskItem;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.RecentStudySession;
import com.studyflow.studyflow.dto.StudyProgress;
import com.studyflow.studyflow.dto.StudyStreak;
import com.studyflow.studyflow.dto.SubjectStudySummary;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.DashboardService;
import com.studyflow.studyflow.util.StudyTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase H integration tests: the dashboard's study statistics aggregation
 * queries execute against the real database (no stubs). Sessions are seeded
 * in a transaction and rolled back, so nothing persists.
 *
 * Verifies the aggregations themselves (today/week totals, counts and
 * groups), the newest-first recency cap, and user isolation: one user's
 * sessions can never appear in another user's dashboard statistics.
 *
 * Dashboard 2.0 additions also verify the study streak rule, the per-subject
 * task-completion snapshot, the true due-today / overdue / due-tomorrow
 * counts and the real upcoming-exam total against seeded data.
 */
@SpringBootTest
@Transactional
class DashboardStudyQueryIntegrationTest {

    private static final String ALICE_EMAIL = "h-dash-alice@example.com";
    private static final String BOB_EMAIL = "h-dash-bob@example.com";

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private Subject saveSubject(User user, String name, String color) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor(color);
        subject.setUser(user);
        return subjectRepository.save(subject);
    }

    private Topic saveTopic(Subject subject, String name) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setSubject(subject);
        return topicRepository.save(topic);
    }

    private StudySession saveSession(Subject subject, Topic topic, StudySessionType type,
                                     LocalDate date, Integer duration) {
        StudySession session = new StudySession();
        session.setSubject(subject);
        session.setTopic(topic);
        session.setSessionType(type);
        session.setSessionDate(date);
        session.setDurationMinutes(duration);
        return studySessionRepository.save(session);
    }

    private Task saveTask(Subject subject, String title, TaskType type,
                          LocalDate due, boolean completed) {
        Task task = new Task();
        task.setTitle(title);
        task.setType(type);
        task.setDueDate(due);
        task.setCompleted(completed);
        task.setSubject(subject);
        return taskRepository.save(task);
    }

    private Exam saveExam(Subject subject, String name, ExamType type,
                          LocalDate date, Integer durationMinutes) {
        Exam exam = new Exam();
        exam.setName(name);
        exam.setExamType(type);
        exam.setExamDate(date);
        exam.setDurationMinutes(durationMinutes);
        exam.setSubject(subject);
        return examRepository.save(exam);
    }

    @Test
    void studyProgress_isAggregatedFromRealUserScopedSessions() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        User alice = saveUser("Alice Study", ALICE_EMAIL);
        User bob = saveUser("Bob Study", BOB_EMAIL);

        Subject database = saveSubject(alice, "Database", "Purple");
        Subject mathematics = saveSubject(bob, "Mathematics", "Blue");
        Topic normalization = saveTopic(database, "Normalization");

        // Alice: six sessions, oldest two fall outside the current week.
        saveSession(database, null, StudySessionType.FOCUS, today, 30);
        saveSession(database, normalization, StudySessionType.REVISION, today, 45);
        saveSession(database, null, StudySessionType.PRACTICE, today.minusDays(6), 60);
        saveSession(database, null, StudySessionType.READING, today.minusDays(30), 120);
        saveSession(database, null, StudySessionType.FOCUS, today, 25);
        saveSession(database, null, StudySessionType.OTHER, today.minusDays(31), 10);

        // Bob: a big session that must NEVER leak into Alice's report.
        saveSession(mathematics, null, StudySessionType.FOCUS, today, 999);

        long dayMinus6InWeek = !today.minusDays(6).isBefore(weekStart) ? 60L : 0L;
        long expectedWeekMinutes = 100 + dayMinus6InWeek;
        long expectedWeekCount = 3 + (dayMinus6InWeek > 0 ? 1 : 0);

        DashboardData aliceData = dashboardService.getDashboardData(ALICE_EMAIL);
        StudyProgress progress = aliceData.studyProgress();

        // Today and week totals come from the real summed minutes.
        assertTrue(progress.hasStudy());
        assertEquals("1h 40m", progress.todayLabel());
        assertEquals(StudyTimeFormatter.formatMinutes(expectedWeekMinutes), progress.weekLabel());
        assertEquals(expectedWeekCount, progress.weekSessionCount());

        // Recent sessions: newest first, capped at five (the 10m oldest is dropped).
        assertEquals(5, progress.recentSessions().size());
        RecentStudySession newest = progress.recentSessions().get(0);
        assertEquals("Database", newest.subjectName());
        assertEquals("Purple", newest.subjectColor());
        assertEquals("25m", newest.durationLabel());
        assertEquals("Focus", newest.sessionTypeLabel());
        assertFalse(progress.recentSessions().stream()
                .anyMatch(session -> "10m".equals(session.durationLabel())));

        // The topic-bearing session keeps its topic name.
        assertTrue(progress.recentSessions().stream()
                .anyMatch(session -> "Normalization".equals(session.topicName())
                        && "Revision".equals(session.sessionTypeLabel())
                        && "45m".equals(session.durationLabel())));

        // Subject summary: real GROUP BY total, highest minutes first.
        assertEquals(1, progress.subjectSummaries().size());
        SubjectStudySummary summary = progress.subjectSummaries().get(0);
        assertEquals("Database", summary.subjectName());
        assertEquals("Purple", summary.color());
        assertEquals("4h 50m", summary.totalLabel());

        // Weekly breakdown: seven days, today carries today's real total.
        assertEquals(7, progress.dailyMinutes().size());
        DayStudyMinutes todayEntry = progress.dailyMinutes().stream()
                .filter(DayStudyMinutes::isToday)
                .findFirst()
                .orElseThrow();
        assertEquals(100, todayEntry.minutes());
        assertEquals("Mon", progress.dailyMinutes().get(0).dayName());

        // Isolation: Bob's session never appears in Alice's report.
        assertFalse(progress.recentSessions().stream()
                .anyMatch(session -> "Mathematics".equals(session.subjectName())));
        assertFalse(progress.subjectSummaries().stream()
                .anyMatch(summaryEntry -> "Mathematics".equals(summaryEntry.subjectName())));
        assertFalse(progress.weekLabel().contains("16h 39m"));
    }

    @Test
    void studyProgress_respectsUserIsolationAndEmptyAccounts() {
        LocalDate today = LocalDate.now();

        User bob = saveUser("Bob Study", BOB_EMAIL);
        Subject mathematics = saveSubject(bob, "Mathematics", "Blue");
        saveSession(mathematics, null, StudySessionType.FOCUS, today, 999);

        // An account with no sessions at all renders honest zeroes.
        saveUser("Empty Study", "h-dash-empty@example.com");
        StudyProgress empty = dashboardService.getDashboardData("h-dash-empty@example.com").studyProgress();

        assertFalse(empty.hasStudy());
        assertEquals("0m", empty.todayLabel());
        assertEquals("0m", empty.weekLabel());
        assertEquals(0, empty.weekSessionCount());
        assertTrue(empty.recentSessions().isEmpty());
        assertEquals(7, empty.dailyMinutes().size());
        assertTrue(empty.dailyMinutes().stream().allMatch(day -> day.minutes() == 0));

        // Bob's own total is present for Bob.
        StudyProgress bobProgress = dashboardService.getDashboardData(BOB_EMAIL).studyProgress();
        assertEquals(StudyTimeFormatter.formatMinutes(999), bobProgress.todayLabel());
    }

    @Test
    void dashboard20_summaryStreakAndCompletion_useRealUserScopedData() {
        LocalDate today = LocalDate.now();

        User alice = saveUser("Alice Two Oh", "h-dash20-alice@example.com");
        User bob = saveUser("Bob Two Oh", "h-dash20-bob@example.com");

        Subject database = saveSubject(alice, "Database", "Purple");
        saveTask(database, "Finish report", TaskType.ASSIGNMENT, today, false);
        saveTask(database, "Solve quiz", TaskType.QUIZ, today, true);
        saveTask(database, "Revise notes", TaskType.REVISION, today, false);
        saveTask(database, "Late homework", TaskType.ASSIGNMENT, today.minusDays(2), false);
        saveTask(database, "Old reading", TaskType.READING, today.minusDays(3), false);
        saveTask(database, "Prep tomorrow", TaskType.REVISION, today.plusDays(1), false);
        saveTask(database, "Done project", TaskType.PROJECT, today.minusDays(5), true);

        Subject mathematics = saveSubject(bob, "Mathematics", "Blue");
        saveTask(mathematics, "Bob's secret task", TaskType.OTHER, today, false);

        saveSession(database, null, StudySessionType.FOCUS, today, 30);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(1), 45);

        saveExam(database, "Database Final", ExamType.FINAL, today.plusDays(4), 120);
        saveExam(database, "DB Quiz", ExamType.QUIZ, today.plusDays(10), 30);
        saveExam(database, "DB Past Midterm", ExamType.MIDTERM, today.minusDays(3), 60);
        saveExam(mathematics, "Bob's Exam", ExamType.MIDTERM, today.plusDays(1), 60);

        DashboardData data = dashboardService.getDashboardData("h-dash20-alice@example.com");

        // Today's tasks: true counts derived from the full due-today set.
        assertEquals(3, data.todayTaskCount());
        assertEquals(1, data.completedTodayCount());
        assertEquals(33, data.todayCompletedPercent());

        // Attention area: real overdue rows plus tasks due tomorrow.
        assertEquals(2, data.overdueCount());
        assertTrue(data.hasOverdueTasks());
        assertEquals(2, data.overdueTasks().size());
        assertTrue(data.overdueTasks().stream()
                .allMatch(DashboardTaskItem::overdue));
        assertTrue(data.hasTomorrowTasks());
        assertEquals(1, data.tomorrowTaskCount());

        // Upcoming exams: honest user-scoped total for the summary card.
        assertEquals(2, data.upcomingExamTotal());

        // Streak: today and yesterday are both real active study dates.
        StudyStreak streak = data.studyStreak();
        assertEquals(2, streak.days());
        assertTrue(streak.activeToday());
        assertTrue(streak.hasRecentActivity());

        // Per-subject task completion: every task in the subject counts.
        assertEquals(1, data.subjectTopics().size());
        DashboardSubjectTopics subjectInfo = data.subjectTopics().get(0);
        assertEquals("Database", subjectInfo.subjectName());
        assertEquals(7, subjectInfo.taskCount());
        assertEquals(2, subjectInfo.completedTaskCount());
        assertEquals(29, subjectInfo.taskCompletionPercent());
        assertTrue(subjectInfo.hasTasks());

        // Isolation: Bob's subject, task and exam never reach Alice.
        assertFalse(data.subjectTopics().stream()
                .anyMatch(info -> "Mathematics".equals(info.subjectName())));
    }

    @Test
    void dashboard20_streak_breaksOnGapOrTodayInactivity() {
        LocalDate today = LocalDate.now();

        User yesterdayOnly = saveUser("Streak Yesterday", "h-dash20-yesterday@example.com");
        Subject physics = saveSubject(yesterdayOnly, "Physics", "Orange");
        saveSession(physics, null, StudySessionType.FOCUS, today.minusDays(1), 20);

        User gapUser = saveUser("Streak Gap", "h-dash20-gap@example.com");
        Subject chemistry = saveSubject(gapUser, "Chemistry", "Red");
        saveSession(chemistry, null, StudySessionType.FOCUS, today.minusDays(4), 20);
        saveSession(chemistry, null, StudySessionType.FOCUS, today.minusDays(3), 20);

        User inactive = saveUser("Streak Inactive", "h-dash20-inactive@example.com");
        saveSubject(inactive, "Biology", "Green");

        // Studied yesterday but not yet today: the streak holds and is not
        // broken mid-day; it simply is not counted as active today.
        StudyStreak yesterday = dashboardService
                .getDashboardData("h-dash20-yesterday@example.com").studyStreak();
        assertEquals(1, yesterday.days());
        assertFalse(yesterday.activeToday());
        assertTrue(yesterday.hasRecentActivity());

        // A gap between yesterday and the last active date resets to zero.
        StudyStreak gap = dashboardService
                .getDashboardData("h-dash20-gap@example.com").studyStreak();
        assertEquals(0, gap.days());

        // No sessions at all: an honest zero, never an invented streak.
        StudyStreak inactiveStreak = dashboardService
                .getDashboardData("h-dash20-inactive@example.com").studyStreak();
        assertEquals(0, inactiveStreak.days());
        assertFalse(inactiveStreak.hasRecentActivity());
    }
}