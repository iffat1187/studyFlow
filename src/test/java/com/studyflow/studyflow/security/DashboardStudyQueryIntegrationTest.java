package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.DashboardData;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.RecentStudySession;
import com.studyflow.studyflow.dto.StudyProgress;
import com.studyflow.studyflow.dto.SubjectStudySummary;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
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
}