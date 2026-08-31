package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Topic information for one of the authenticated user's subjects on the
 * dashboard.
 *
 * {@code topicCount} and the {@code previewTopics} names come from real
 * Topic records (user-scoped); the preview is capped so a subject with many
 * topics never floods the dashboard.
 */
public record DashboardSubjectTopics(
        Long subjectId,
        String subjectName,
        String subjectColor,
        long topicCount,
        boolean hasTopics,
        List<String> previewTopics,
        boolean hasMoreTopics
) {
}