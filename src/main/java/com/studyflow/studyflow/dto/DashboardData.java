package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Data backing the authenticated USER dashboard.
 *
 * Every value is produced by the dashboard service from real application
 * state. Topics and tasks are resolved through user-scoped repository
 * queries — one user can never observe another user's data.
 *
 * {@code subjectTopics} carries per-subject topic counts, a small name
 * preview and the subject's task-completion snapshot. {@code todayTasks},
 * {@code overdueTasks}, {@code tomorrowTasks} and {@code upcomingTasks}
 * hold the user's actionable task items in their respective groups, and
 * {@code studyStreak} is the user's real consecutive study-day count. The
 * remaining fields deliver exam, study, and setup-checklist state from
 * existing modules.
 */
public record DashboardData(
        String userName,
        String displayName,
        String greeting,
        String greetingMessage,
        String dateLabel,
        String dayLabel,
        List<SubjectDto> subjects,
        boolean hasSubjects,
        long subjectCount,
        List<DashboardSubjectTopics> subjectTopics,
        boolean hasSubjectTopics,
        List<DashboardTaskItem> todayTasks,
        boolean hasTodayTasks,
        int todayTaskCount,
        int completedTodayCount,
        int todayCompletedPercent,
        long overdueCount,
        List<DashboardTaskItem> overdueTasks,
        boolean hasOverdueTasks,
        List<DashboardTaskItem> tomorrowTasks,
        boolean hasTomorrowTasks,
        int tomorrowTaskCount,
        List<DashboardTaskItem> upcomingTasks,
        boolean hasUpcomingTasks,
        int upcomingTaskCount,
        List<UpcomingExam> upcomingExams,
        boolean hasUpcomingExams,
        long upcomingExamCount,
        long upcomingExamTotal,
        UpcomingExam nextExam,
        StudyStreak studyStreak,
        List<SetupStep> setupSteps,
        int setupCompleted,
        int setupTotal,
        int setupPercent,
        StudyProgress studyProgress
) {
}