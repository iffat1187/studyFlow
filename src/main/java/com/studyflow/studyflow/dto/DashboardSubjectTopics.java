package com.studyflow.studyflow.dto;

import java.util.List;

/**
 * Snapshot of one of the authenticated user's subjects on the dashboard.
 *
 * {@code topicCount} and the {@code previewTopics} names come from real
 * Topic records (user-scoped); the preview is capped so a subject with many
 * topics never floods the dashboard.
 *
 * The task fields carry the subject's real task completion from the existing
 * Task model: {@code taskCount} is the total number of tasks in the subject,
 * {@code completedTaskCount} how many are marked complete and
 * {@code taskCompletionPercent} their ratio (0 when the subject has no
 * tasks). The metric is labelled "Task completion" in the template — it is
 * task-based progress, not a fabricated academic grade.
 */
public record DashboardSubjectTopics(
        Long subjectId,
        String subjectName,
        String subjectColor,
        long topicCount,
        boolean hasTopics,
        List<String> previewTopics,
        boolean hasMoreTopics,
        long taskCount,
        long completedTaskCount,
        int taskCompletionPercent,
        boolean hasTasks
) {
}