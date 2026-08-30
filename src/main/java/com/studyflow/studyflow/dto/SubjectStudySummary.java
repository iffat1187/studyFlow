package com.studyflow.studyflow.dto;

/**
 * Total study time for a single subject, used in the dashboard's
 * "study time by subject" section.
 *
 * Computed by a database-side GROUP BY scoped to the authenticated user;
 * only subjects with at least one logged session appear.
 */
public record SubjectStudySummary(
        Long subjectId,
        String subjectName,
        String color,
        String totalLabel
) {
}