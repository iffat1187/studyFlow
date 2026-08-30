package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.AnalyticsDto;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.RecentActivity;
import com.studyflow.studyflow.dto.SessionTypeAnalytics;
import com.studyflow.studyflow.dto.SubjectAnalytics;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.AnalyticsService;
import com.studyflow.studyflow.util.StudyTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AnalyticsService}.
 *
 * All statistics come from the authenticated user's REAL StudySession data.
 * Sums, counts and groups are computed by user-scoped database queries (no
 * all-sessions fetch + in-memory filtering); streaks are derived from the
 * small list of distinct active dates.
 *
 * Date rules (consistent with the rest of the application):
 *
 * - current week: from the start of the current week (Monday) through today
 * - current month: from the first day of the current month through today
 *
 * An unknown account (or one with no sessions) yields honest zeroes rather
 * than fabricated numbers.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final int DAYS_IN_WEEK = 7;
    private static final int RECENT_ACTIVITY_LIMIT = 5;

    private static final String[] TYPE_COLORS = {
            "#8B5CF6", "#38BDF8", "#22C55E", "#F59E0B",
            "#F472B6", "#60A5FA", "#A78BFA"
    };

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;

    /**
     * Constructor injection.
     */
    public AnalyticsServiceImpl(UserRepository userRepository,
                                StudySessionRepository studySessionRepository) {
        this.userRepository = userRepository;
        this.studySessionRepository = studySessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsDto getAnalytics(String userEmail) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);

        User user = resolveUser(userEmail);
        if (user == null) {
            return emptyAnalytics(today, weekStart);
        }

        long totalMinutes = nullSafeLong(
                studySessionRepository.sumDurationMinutesBySubject_User(user));
        long weekMinutes = nullSafeLong(studySessionRepository
                .sumDurationMinutesBySubject_UserAndSessionDateBetween(user, weekStart, today));
        long monthMinutes = nullSafeLong(studySessionRepository
                .sumDurationMinutesBySubject_UserAndSessionDateBetween(user, monthStart, today));
        long totalSessions = studySessionRepository.countBySubject_User(user);

        List<DayStudyMinutes> dailyStudyData =
                dailyStudyData(weekStart, today, user);
        List<SubjectAnalytics> subjectStudyData = subjectStudyData(user);
        List<SessionTypeAnalytics> sessionTypeStudyData = sessionTypeStudyData(user);

        Set<LocalDate> activeDates = new HashSet<>(
                studySessionRepository.findDistinctActiveDatesBySubject_User(user));

        List<RecentActivity> recentActivity = studySessionRepository
                .findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(user)
                .stream()
                .limit(RECENT_ACTIVITY_LIMIT)
                .map(this::toRecentActivity)
                .collect(Collectors.toList());

        return new AnalyticsDto(
                totalMinutes,
                StudyTimeFormatter.formatMinutes(totalMinutes),
                weekMinutes,
                StudyTimeFormatter.formatMinutes(weekMinutes),
                monthMinutes,
                StudyTimeFormatter.formatMinutes(monthMinutes),
                totalSessions,
                currentStreak(today, activeDates),
                longestStreak(activeDates),
                totalSessions > 0,
                dailyStudyData,
                subjectStudyData,
                sessionTypeStudyData,
                recentActivity
        );
    }

    /**
     * Seven-day (Monday-Sunday) study minutes for the current week.
     * Days without activity carry 0 and a "0m" label; the bar percentage is
     * relative to the week's busiest day so the chart scales naturally.
     */
    private List<DayStudyMinutes> dailyStudyData(LocalDate weekStart,
                                                 LocalDate today,
                                                 User user) {
        Map<LocalDate, Long> minutesByDate = new HashMap<>();
        for (Object[] row : studySessionRepository
                .sumDurationMinutesByDateForUser(user, weekStart, today)) {
            minutesByDate.put((LocalDate) row[0], ((Number) row[1]).longValue());
        }

        long weekMax = 0;
        long[] minutes = new long[DAYS_IN_WEEK];
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            minutes[i] = minutesByDate.getOrDefault(weekStart.plusDays(i), 0L);
            weekMax = Math.max(weekMax, minutes[i]);
        }

        List<DayStudyMinutes> days = new ArrayList<>(DAYS_IN_WEEK);
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            LocalDate day = weekStart.plusDays(i);
            int barPercent = weekMax == 0 ? 0
                    : (int) Math.round(minutes[i] * 100.0 / weekMax);
            days.add(new DayStudyMinutes(
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    (int) minutes[i],
                    StudyTimeFormatter.formatMinutes(minutes[i]),
                    barPercent,
                    day.equals(today)
            ));
        }
        return days;
    }

    /**
     * Per-subject study totals, ranked by minutes descending. Only subjects
     * with at least one logged session appear; the chart color reuses the
     * subject's own color where it maps to the StudyFlow palette.
     */
    private List<SubjectAnalytics> subjectStudyData(User user) {
        List<Object[]> rows = studySessionRepository.sumDurationMinutesBySubjectForUser(user);
        rows.sort(Comparator.comparingLong(row -> -((Number) row[3]).longValue()));
        List<SubjectAnalytics> subjects = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            subjects.add(new SubjectAnalytics(
                    (String) row[1],
                    subjectColorHex((String) row[2]),
                    ((Number) row[3]).longValue(),
                    StudyTimeFormatter.formatMinutes(((Number) row[3]).longValue())
            ));
        }
        return subjects;
    }

    /**
     * Per-session-type study totals, ranked by minutes descending, with
     * human-friendly labels derived from the real enum names.
     */
    private List<SessionTypeAnalytics> sessionTypeStudyData(User user) {
        List<Object[]> rows = studySessionRepository.sumDurationMinutesByTypeForUser(user);
        rows.sort(Comparator.comparingLong(row -> -((Number) row[1]).longValue()));
        List<SessionTypeAnalytics> types = new ArrayList<>(rows.size());
        int rank = 0;
        for (Object[] row : rows) {
            long minutes = ((Number) row[1]).longValue();
            types.add(new SessionTypeAnalytics(
                    sessionTypeLabel((StudySessionType) row[0]),
                    minutes,
                    StudyTimeFormatter.formatMinutes(minutes),
                    TYPE_COLORS[rank % TYPE_COLORS.length]
            ));
            rank++;
        }
        return types;
    }

    /**
     * Current study streak: consecutive calendar days ending today on which
     * the user has at least one recorded session with a positive duration.
     * A break (missing yesterday) stops the count; no sessions → 0.
     */
    private int currentStreak(LocalDate today, Set<LocalDate> activeDates) {
        int streak = 0;
        LocalDate day = today;
        while (activeDates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /**
     * Longest study streak from the distinct active dates, computed from
     * consecutive calendar days (never from session count).
     */
    private int longestStreak(Set<LocalDate> activeDates) {
        if (activeDates.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = activeDates.stream().sorted().collect(Collectors.toList());
        int longest = 1;
        int running = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
                running++;
            } else {
                running = 1;
            }
            longest = Math.max(longest, running);
        }
        return longest;
    }

    /**
     * Wrap a user-scoped session into a small recent-activity row.
     */
    private RecentActivity toRecentActivity(StudySession session) {
        Subject subject = session.getSubject();
        return new RecentActivity(
                session.getSessionDate(),
                subject != null ? subject.getName() : null,
                StudyTimeFormatter.formatMinutes(
                        session.getDurationMinutes() == null ? 0 : session.getDurationMinutes())
        );
    }

    /**
     * Map a subject's color name to the StudyFlow chart palette, falling
     * back to the primary violet for colors outside the known set.
     */
    private String subjectColorHex(String color) {
        if (color == null) {
            return "#A78BFA";
        }
        switch (color.toLowerCase(Locale.ENGLISH)) {
            case "purple":
                return "#8B5CF6";
            case "blue":
                return "#38BDF8";
            case "green":
                return "#22C55E";
            case "orange":
                return "#F59E0B";
            case "red":
                return "#EF4444";
            default:
                return "#A78BFA";
        }
    }

    /**
     * Human-friendly label for a session type, e.g.
     * EXAM_PREPARATION → "Exam Preparation".
     */
    private String sessionTypeLabel(StudySessionType type) {
        if (type == null) {
            return "Session";
        }
        return Arrays.stream(type.name().toLowerCase(Locale.ENGLISH).split("_"))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
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

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * Honest zero state for an account with no resolvable user or no
     * sessions: real "0m", "0 sessions" and "0 day streak" values with an
     * empty (all-zero) weekly breakdown.
     */
    private AnalyticsDto emptyAnalytics(LocalDate today, LocalDate weekStart) {
        List<DayStudyMinutes> emptyDays = new ArrayList<>(DAYS_IN_WEEK);
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            LocalDate day = weekStart.plusDays(i);
            emptyDays.add(new DayStudyMinutes(
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    0,
                    "0m",
                    0,
                    day.equals(today)
            ));
        }
        return new AnalyticsDto(
                0, "0m",
                0, "0m",
                0, "0m",
                0,
                0, 0,
                false,
                emptyDays,
                List.of(),
                List.of(),
                List.of()
        );
    }
}