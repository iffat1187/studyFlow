package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.AnalyticsDto;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.RecentActivity;
import com.studyflow.studyflow.dto.SessionTypeAnalytics;
import com.studyflow.studyflow.dto.SubjectAnalytics;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.AnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase I service-level tests.
 *
 * Exercises {@link AnalyticsServiceImpl} with mocked repositories so the
 * aggregation, formatting, ranges and streak rules can be verified without
 * a browser. The real SQL behind each repository method is verified against
 * the live database in {@code AnalyticsQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    private User userA;
    private User userB;
    private LocalDate today;
    private LocalDate weekStart;
    private LocalDate monthStart;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(1L).name("User A").email("a@example.com")
                .password("encoded").role(Role.USER).build();
        userB = User.builder().id(2L).name("User B").email("b@example.com")
                .password("encoded").role(Role.USER).build();

        today = LocalDate.now();
        weekStart = today.with(DayOfWeek.MONDAY);
        monthStart = today.withDayOfMonth(1);

        when(userRepository.findByEmail("a@example.com"))
                .thenReturn(Optional.of(userA));
        when(userRepository.findByEmail("b@example.com"))
                .thenReturn(Optional.of(userB));
    }

    @Test
    void unknownUser_rendersHonestZeroState() {
        when(userRepository.findByEmail("ghost@example.com"))
                .thenReturn(Optional.empty());

        AnalyticsDto data = analyticsService.getAnalytics("ghost@example.com");

        assertThat(data.hasStudy()).isFalse();
        assertThat(data.totalStudyMinutes()).isZero();
        assertThat(data.totalStudyTime()).isEqualTo("0m");
        assertThat(data.weekStudyTime()).isEqualTo("0m");
        assertThat(data.monthStudyTime()).isEqualTo("0m");
        assertThat(data.totalSessions()).isZero();
        assertThat(data.currentStreak()).isZero();
        assertThat(data.longestStreak()).isZero();
        assertThat(data.dailyStudyData()).hasSize(7);
        assertThat(data.dailyStudyData()).allMatch(day -> day.minutes() == 0);
        assertThat(data.subjectStudyData()).isEmpty();
        assertThat(data.sessionTypeStudyData()).isEmpty();
        assertThat(data.recentActivity()).isEmpty();
    }

    @Test
    void totalStudyTime_sumsRealMinutesAcrossAllSessions() {
        stubNoActivity();

        when(studySessionRepository.sumDurationMinutesBySubject_User(userA))
                .thenReturn(135L);

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.totalStudyMinutes()).isEqualTo(135L);
        assertThat(data.totalStudyTime()).isEqualTo("2h 15m");
    }

    @Test
    void weekAndMonthTotals_useMondayStartAndMonthStartRespectively() {
        stubNoActivity();

        when(studySessionRepository
                .sumDurationMinutesBySubject_UserAndSessionDateBetween(eq(userA), eq(weekStart), eq(today)))
                .thenReturn(125L);
        when(studySessionRepository
                .sumDurationMinutesBySubject_UserAndSessionDateBetween(eq(userA), eq(monthStart), eq(today)))
                .thenReturn(300L);

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.weekStudyMinutes()).isEqualTo(125L);
        assertThat(data.weekStudyTime()).isEqualTo("2h 5m");
        assertThat(data.monthStudyMinutes()).isEqualTo(300L);
        assertThat(data.monthStudyTime()).isEqualTo("5h");
    }

    @Test
    void totalSessions_comesFromTheRealAllTimeCount() {
        stubNoActivity();

        when(studySessionRepository.countBySubject_User(userA)).thenReturn(9L);

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.totalSessions()).isEqualTo(9L);
        assertThat(data.hasStudy()).isTrue();
    }

    @Test
    void dailyTrend_rendersSevenRealDaysWithTodayFlag() {
        stubNoActivity();

        when(studySessionRepository.sumDurationMinutesByDateForUser(eq(userA), eq(weekStart), eq(today)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{today, 30L},
                        new Object[]{today.minusDays(1), 45L}
                ));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.dailyStudyData()).hasSize(7);
        assertThat(data.dailyStudyData().get(0).dayName()).isEqualTo("Mon");
        DayStudyMinutes todayEntry = data.dailyStudyData().stream()
                .filter(DayStudyMinutes::isToday)
                .findFirst()
                .orElseThrow();
        assertThat(todayEntry.minutes()).isEqualTo(30);
        assertThat(todayEntry.minutesLabel()).isEqualTo("30m");
        assertThat(data.dailyStudyData().stream()
                .filter(day -> !day.isToday())
                .map(DayStudyMinutes::minutes))
                .anyMatch(minutes -> minutes == 45);
        // Percentages are relative to the week's busiest day.
        DayStudyMinutes monday = data.dailyStudyData().get(0);
        assertThat(monday.barPercent()).isIn(0, 15);
    }

    @Test
    void subjectBreakdown_ranksByMinutesAndReusesSubjectColors() {
        stubNoActivity();

        when(studySessionRepository.sumDurationMinutesBySubjectForUser(userA))
                .thenReturn(new ArrayList<>(List.of(
                        new Object[]{1L, "Mathematics", "blue", 120L},
                        new Object[]{2L, "Database", "purple", 60L}
                )));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.subjectStudyData()).extracting(SubjectAnalytics::subjectName)
                .containsExactly("Mathematics", "Database");
        SubjectAnalytics top = data.subjectStudyData().get(0);
        assertThat(top.minutes()).isEqualTo(120L);
        assertThat(top.minutesLabel()).isEqualTo("2h");
        assertThat(top.colorHex()).isEqualTo("#38BDF8");
        assertThat(data.subjectStudyData().get(1).colorHex()).isEqualTo("#8B5CF6");
    }

    @Test
    void sessionTypeBreakdown_usesFriendlyLabelsAndPaletteColors() {
        stubNoActivity();

        when(studySessionRepository.sumDurationMinutesByTypeForUser(userA))
                .thenReturn(new ArrayList<>(List.of(
                        new Object[]{StudySessionType.REVISION, 30L},
                        new Object[]{StudySessionType.FOCUS, 60L},
                        new Object[]{StudySessionType.EXAM_PREPARATION, 0L}
                )));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.sessionTypeStudyData()).extracting(SessionTypeAnalytics::typeLabel)
                .containsExactly("Focus", "Revision", "Exam Preparation");
        assertThat(data.sessionTypeStudyData()).extracting(SessionTypeAnalytics::minutes)
                .containsExactly(60L, 30L, 0L);
        assertThat(data.sessionTypeStudyData().get(0).colorHex())
                .isEqualTo("#8B5CF6");
        assertThat(data.sessionTypeStudyData().get(1).colorHex())
                .isEqualTo("#38BDF8");
        assertThat(data.sessionTypeStudyData().get(2).minutesLabel()).isEqualTo("0m");
    }

    @Test
    void currentStreak_countsConsecutiveActiveDaysEndingToday() {
        stubNoActivity();

        when(studySessionRepository.findDistinctActiveDatesBySubject_User(userA))
                .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2)));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.currentStreak()).isEqualTo(3);
    }

    @Test
    void currentStreak_breaksOnGapAndStopsAtToday() {
        stubNoActivity();

        when(studySessionRepository.findDistinctActiveDatesBySubject_User(userA))
                .thenReturn(List.of(today.minusDays(2), today.minusDays(3)));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.currentStreak()).isZero();
    }

    @Test
    void longestStreak_measuresTheLongestConsecutiveRun() {
        stubNoActivity();

        when(studySessionRepository.findDistinctActiveDatesBySubject_User(userA))
                .thenReturn(List.of(
                        today, today.minusDays(1), today.minusDays(2),
                        today.minusDays(4), today.minusDays(5),
                        today.minusDays(9)
                ));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.currentStreak()).isEqualTo(3);
        assertThat(data.longestStreak()).isEqualTo(3);

        when(studySessionRepository.findDistinctActiveDatesBySubject_User(userA))
                .thenReturn(List.of(
                        today.minusDays(10), today.minusDays(11), today.minusDays(12), today.minusDays(13)
                ));

        AnalyticsDto older = analyticsService.getAnalytics("a@example.com");
        assertThat(older.currentStreak()).isZero();
        assertThat(older.longestStreak()).isEqualTo(4);
    }

    @Test
    void recentActivity_mapsNewestFirstEntriesToDisplayRows() {
        stubNoActivity();

        Subject maths = new Subject();
        maths.setName("Mathematics");
        maths.setColor("blue");

        StudySession newest = new StudySession();
        newest.setSubject(maths);
        newest.setSessionDate(today);
        newest.setDurationMinutes(60);

        StudySession older = new StudySession();
        older.setSubject(null);
        older.setSessionDate(today.minusDays(1));
        older.setDurationMinutes(45);

        when(studySessionRepository
                .findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(userA))
                .thenReturn(List.of(newest, older));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.recentActivity()).hasSize(2);
        RecentActivity first = data.recentActivity().get(0);
        assertThat(first.subjectName()).isEqualTo("Mathematics");
        assertThat(first.sessionDate()).isEqualTo(today);
        assertThat(first.durationLabel()).isEqualTo("1h");
        assertThat(data.recentActivity().get(1).durationLabel()).isEqualTo("45m");
    }

    @Test
    void analytics_neverMixesAnotherUsersSessions() {
        stubNoActivity();

        when(studySessionRepository.sumDurationMinutesBySubject_User(userA))
                .thenReturn(300L);
        when(studySessionRepository.sumDurationMinutesBySubject_User(userB))
                .thenReturn(120L);
        when(studySessionRepository.countBySubject_User(userA)).thenReturn(2L);

        AnalyticsDto a = analyticsService.getAnalytics("a@example.com");
        AnalyticsDto b = analyticsService.getAnalytics("b@example.com");

        assertThat(a.totalStudyMinutes()).isEqualTo(300L);
        assertThat(a.totalSessions()).isEqualTo(2L);
        assertThat(b.totalStudyMinutes()).isEqualTo(120L);
        // User B's count was never stubbed → honest zero.
        assertThat(b.totalSessions()).isZero();
        assertThat(b.hasStudy()).isFalse();
    }

    @Test
    void todayOnlyActivity_yieldsStreakOfOne() {
        stubNoActivity();

        when(studySessionRepository.findDistinctActiveDatesBySubject_User(userA))
                .thenReturn(List.of(today));

        AnalyticsDto data = analyticsService.getAnalytics("a@example.com");

        assertThat(data.currentStreak()).isEqualTo(1);
        assertThat(data.longestStreak()).isEqualTo(1);
    }

    private void stubNoActivity() {
        when(studySessionRepository.sumDurationMinutesBySubject_User(any())).thenReturn(0L);
        when(studySessionRepository
                .sumDurationMinutesBySubject_UserAndSessionDateBetween(any(), any(), any())).thenReturn(0L);
        when(studySessionRepository.countBySubject_User(any())).thenReturn(0L);
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(any()))
                .thenReturn(List.of());
        when(studySessionRepository.sumDurationMinutesByDateForUser(any(), any(), any()))
                .thenReturn(List.of());
        when(studySessionRepository.sumDurationMinutesBySubjectForUser(any()))
                .thenReturn(new ArrayList<>());
        when(studySessionRepository.sumDurationMinutesByTypeForUser(any()))
                .thenReturn(new ArrayList<>());
        when(studySessionRepository
                .findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(any()))
                .thenReturn(List.of());
    }
}