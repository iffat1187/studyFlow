package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Data backing the authenticated USER dashboard.
 *
 * Every value is produced by the dashboard service from real application
 * state. Topics and tasks are resolved through user-scoped repository
 * queries — one user can never observe another user's data.
 *
 * The {@code subjectTopics} list carries per-subject topic counts and
 * a small name preview, {@code todayTasks} and {@code upcomingTasks}
 * hold the user's actionable task items, and the remaining fields
 * deliver exam, study, and setup-checklist state from existing modules.
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
        List<DashboardTaskItem> upcomingTasks,
        boolean hasUpcomingTasks,
        int upcomingTaskCount,
        List<UpcomingExam> upcomingExams,
        boolean hasUpcomingExams,
        long upcomingExamCount,
        UpcomingExam nextExam,
        List<SetupStep> setupSteps,
        int setupCompleted,
        int setupTotal,
        int setupPercent,
        StudyProgress studyProgress
) {
}