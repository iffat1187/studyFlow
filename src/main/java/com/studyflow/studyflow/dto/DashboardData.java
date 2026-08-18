package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Data backing the authenticated USER dashboard.
 *
 * Every value is produced by the dashboard service from real application
 * state. Sections that depend on modules which do not exist yet (tasks,
 * exams, study sessions) are deliberately left out of this model so the
 * template can render honest empty/future states instead of fake numbers.
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
        List<SetupStep> setupSteps,
        int setupCompleted,
        int setupTotal,
        int setupPercent
) {
}
