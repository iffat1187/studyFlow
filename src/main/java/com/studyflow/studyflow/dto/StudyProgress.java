package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Study-session statistics backing the dashboard's Study Progress section.
 *
 * Every value is aggregated from the authenticated user's real sessions by
 * the dashboard service (database-side, user-scoped queries only). There
 * are no fabricated numbers: empty accounts render "0m" labels and empty
 * lists, which the template turns into honest empty states.
 *
 * The week is defined from the start of the current week through today
 * (Monday rule), so {@link #todayLabel()} is always part of
 * {@link #weekLabel()}.
 */
public record StudyProgress(
        String todayLabel,
        String weekLabel,
        long weekSessionCount,
        boolean hasStudy,
        List<DayStudyMinutes> dailyMinutes,
        List<RecentStudySession> recentSessions,
        boolean hasRecentSessions,
        List<SubjectStudySummary> subjectSummaries,
        boolean hasSubjectSummaries
) {
}