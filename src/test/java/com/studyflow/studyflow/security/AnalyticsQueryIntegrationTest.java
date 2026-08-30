package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.AnalyticsDto;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.RecentActivity;
import com.studyflow.studyflow.dto.SessionTypeAnalytics;
import com.studyflow.studyflow.dto.SubjectAnalytics;
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
import com.studyflow.studyflow.service.AnalyticsService;
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
 * Sub-Phase I integration tests: every analytics aggregation query executes
 * against the real database (no stubs) using the same user-scoped JPQL the
 * page uses at runtime. Sessions are seeded inside a transaction and rolled
 * back, so nothing persists.
 *
 * Verifies the real SQL behind all-time/week/month totals, session counts,
 * distinct active-date streak support, subject and session-type groupings,
 * the newest-first recency cap, date-boundary correctness and user
 * isolation.
 */
@SpringBootTest
@Transactional
class AnalyticsQueryIntegrationTest {

    private static final String ALICE_EMAIL = "i-an-alice@example.com";
    private static final String BOB_EMAIL = "i-an-bob@example.com";
    private static final String ZERO_EMAIL = "i-an-zero@example.com";
    private static final String EMPTY_EMAIL = "i-an-empty@example.com";

    @Autowired
    private AnalyticsService analyticsService;

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
                                     LocalDate date, LocalTime startTime, Integer duration) {
        StudySession session = new StudySession();
        session.setSubject(subject);
        session.setTopic(topic);
        session.setSessionType(type);
        session.setSessionDate(date);
        session.setStartTime(startTime);
        session.setDurationMinutes(duration);
        return studySessionRepository.save(session);
    }

    @Test
    void analytics_areAggregatedFromRealUserScopedSessions() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);

        User alice = saveUser("Alice Analytics", ALICE_EMAIL);
        User bob = saveUser("Bob Analytics", BOB_EMAIL);

        Subject database = saveSubject(alice, "Database", "Purple");
        Topic normalization = saveTopic(database, "Normalization");
        Subject mathematics = saveSubject(bob, "Mathematics-Bob", "Blue");

        // Alice: two sessions today (one with a topic), then a 3-day run,
        // a gap, one more day, a 4-day run a week+ later, and one old day.
        saveSession(database, null, StudySessionType.FOCUS, today, LocalTime.of(9, 0), 30);
        saveSession(database, normalization, StudySessionType.REVISION, today, LocalTime.of(10, 0), 45);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(1), null, 20);
        saveSession(database, null, StudySessionType.PRACTICE, today.minusDays(2), null, 15);
        saveSession(database, null, StudySessionType.READING, today.minusDays(4), null, 25);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(10), null, 40);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(11), null, 40);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(12), null, 40);
        saveSession(database, null, StudySessionType.FOCUS, today.minusDays(13), null, 40);
        saveSession(database, null, StudySessionType.OTHER, today.minusDays(31), null, 10);
        // A zero-minute placeholder: counted as a logged session, but its day
        // must NOT count toward any streak (positive duration required).
        saveSession(database, null, StudySessionType.FOCUS, today, null, 0);

        // Bob: a big session that must never leak into Alice's analytics.
        saveSession(mathematics, null, StudySessionType.FOCUS, today, null, 999);

        long total = 30 + 45 + 20 + 15 + 25 + 40 * 4 + 10; // 305
        long week = 0;
        long month = 0;
        for (int[] spec : new int[][]{{0, 30}, {0, 45}, {1, 20}, {2, 15},
                {4, 25}, {10, 40}, {11, 40}, {12, 40}, {13, 40}, {31, 10}}) {
            LocalDate date = today.minusDays(spec[0]);
            if (!date.isBefore(weekStart)) {
                week += spec[1];
            }
            if (!date.isBefore(monthStart)) {
                month += spec[1];
            }
        }

        AnalyticsDto data = analyticsService.getAnalytics(ALICE_EMAIL);

        // Real, correct totals and formatted labels.
        assertTrue(data.hasStudy());
        assertEquals(total, data.totalStudyMinutes());
        assertEquals("5h 5m", data.totalStudyTime());
        assertEquals(week, data.weekStudyMinutes());
        assertEquals(month, data.monthStudyMinutes());
        assertEquals(11, data.totalSessions());

        // Streaks come from distinct active dates: today's two sessions count
        // once, the zero-minute session adds nothing, and the older run is 4.
        assertEquals(3, data.currentStreak());
        assertEquals(4, data.longestStreak());

        // Weekday series: seven real days, Monday first, today carries 75m.
        assertEquals(7, data.dailyStudyData().size());
        assertEquals("Mon", data.dailyStudyData().get(0).dayName());
        DayStudyMinutes todayEntry = data.dailyStudyData().stream()
                .filter(DayStudyMinutes::isToday)
                .findFirst()
                .orElseThrow();
        assertEquals(75, todayEntry.minutes());

        // Subject breakdown: real GROUP BY, ranked, with the subject's color.
        assertEquals(1, data.subjectStudyData().size());
        SubjectAnalytics subject = data.subjectStudyData().get(0);
        assertEquals("Database", subject.subjectName());
        assertEquals("#8B5CF6", subject.colorHex());
        assertEquals("5h 5m", subject.minutesLabel());

        // Session-type breakdown: real GROUP BY, ranked by minutes.
        assertEquals(List.of("Focus", "Revision", "Reading", "Practice", "Other"),
                data.sessionTypeStudyData().stream()
                        .map(SessionTypeAnalytics::typeLabel)
                        .toList());
        assertEquals("3h 30m", data.sessionTypeStudyData().get(0).minutesLabel());
        assertEquals("10m", data.sessionTypeStudyData().get(4).minutesLabel());

        // Recent activity: newest first, capped at five.
        assertEquals(5, data.recentActivity().size());
        RecentActivity newest = data.recentActivity().get(0);
        assertEquals(today, newest.sessionDate());
        assertEquals("Database", newest.subjectName());
        assertEquals("45m", newest.durationLabel());

        // Isolation: Bob's session never leaks into Alice's analytics.
        assertFalse(data.recentActivity().stream()
                .anyMatch(activity -> "Mathematics-Bob".equals(activity.subjectName())));
        assertFalse(data.subjectStudyData().stream()
                .anyMatch(s -> "Mathematics-Bob".equals(s.subjectName())));
        assertEquals(305, data.totalStudyMinutes());
    }

    @Test
    void analytics_respectUserIsolationAndZeroMinuteSessions() {
        LocalDate today = LocalDate.now();

        User bob = saveUser("Bob Analytics", BOB_EMAIL);
        Subject mathematics = saveSubject(bob, "Mathematics-Bob", "Blue");
        saveSession(mathematics, null, StudySessionType.FOCUS, today, null, 999);

        User zero = saveUser("Zero Analytics", ZERO_EMAIL);
        Subject zeroSubject = saveSubject(zero, "Quiet", "Green");
        // Only a zero-minute session: logged, positive-duration streak NOT earned.
        saveSession(zeroSubject, null, StudySessionType.OTHER, today, null, 0);

        AnalyticsDto bobData = analyticsService.getAnalytics(BOB_EMAIL);
        assertEquals(999, bobData.totalStudyMinutes());
        assertEquals("16h 39m", bobData.totalStudyTime());
        assertEquals(1, bobData.currentStreak());

        AnalyticsDto zeroData = analyticsService.getAnalytics(ZERO_EMAIL);
        assertTrue(zeroData.hasStudy());
        assertEquals(1, zeroData.totalSessions());
        assertEquals(0, zeroData.currentStreak());
        assertEquals(0, zeroData.longestStreak());
        assertTrue(zeroData.subjectStudyData().stream()
                .anyMatch(s -> "Quiet".equals(s.subjectName())));

        // An account with no sessions at all renders honest zeroes.
        saveUser("Empty Analytics", EMPTY_EMAIL);
        AnalyticsDto empty = analyticsService.getAnalytics(EMPTY_EMAIL);
        assertFalse(empty.hasStudy());
        assertEquals("0m", empty.totalStudyTime());
        assertEquals("0m", empty.weekStudyTime());
        assertEquals("0m", empty.monthStudyTime());
        assertEquals(0, empty.totalSessions());
        assertEquals(0, empty.currentStreak());
        assertEquals(0, empty.longestStreak());
        assertTrue(empty.recentActivity().isEmpty());
        assertEquals(7, empty.dailyStudyData().size());
        assertTrue(empty.dailyStudyData().stream().allMatch(day -> day.minutes() == 0));
    }
}