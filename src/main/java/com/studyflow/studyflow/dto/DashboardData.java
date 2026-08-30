package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Data backing the authenticated USER dashboard.
 *
 * Every value is produced by the dashboard service from real application
 * state. The upcoming-exam fields carry the user's real exams (soonest
 * first, capped at {@link com.studyflow.studyflow.service.impl.DashboardServiceImpl#DASHBOARD_EXAM_LIMIT}),
 * with {@code nextExam} highlighting the single nearest one. The
 * {@code studyProgress} field aggregates the user's real study sessions
 * for the Study Progress and Recent Study Sessions sections.
 *
 * Sections that depend on modules which do not exist yet (tasks,
 * analytics) are deliberately left out of this model so the template can
 * render honest empty/future states instead of fake numbers.
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