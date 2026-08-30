package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Data backing the authenticated user's /analytics page.
 *
 * Every value is aggregated from the user's REAL StudySession records by
 * {@code AnalyticsService} using user-scoped database queries. There are no
 * fabricated statistics; an account with no sessions renders honest zeroes
 * ("0m", "0 sessions", "0 day streak") and an empty {@code dailyStudyData}.
 *
 * Chart data is provided by the backend in list form; Chart.js on the page
 * only renders it. The daily series reuses {@link DayStudyMinutes} so no
 * overlapping DTO is introduced.
 */
public record AnalyticsDto(
        long totalStudyMinutes,
        String totalStudyTime,
        long weekStudyMinutes,
        String weekStudyTime,
        long monthStudyMinutes,
        String monthStudyTime,
        long totalSessions,
        int currentStreak,
        int longestStreak,
        boolean hasStudy,
        List<DayStudyMinutes> dailyStudyData,
        List<SubjectAnalytics> subjectStudyData,
        List<SessionTypeAnalytics> sessionTypeStudyData,
        List<RecentActivity> recentActivity
) {
}