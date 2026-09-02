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
 *
 * The weekly goal comes from the user's own {@code UserSettings} record
 * (single source of truth, shared with the Focus Timer defaults). It is
 * purely descriptive: the goal value is real settings data and
 * {@link #weeklyGoalPercent()} is derived from the week's real minutes, so
 * the dashboard never invents progress for a user without a goal.
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
        boolean hasSubjectSummaries,
        String weeklyGoalLabel,
        int weeklyGoalPercent,
        String totalLabel,
        boolean hasTotal
) {
}