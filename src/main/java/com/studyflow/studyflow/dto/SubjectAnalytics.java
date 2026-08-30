package com.studyflow.studyflow.dto;

/**
 * One subject's study total for the analytics page.
 *
 * Backend-derived from the authenticated user's real sessions (database
 * GROUP BY). {@code minutes} feeds the chart, {@code minutesLabel} the
 * supporting text, and {@code colorHex} reuses the subject's own color so
 * the doughnut matches its dashboard identity.
 */
public record SubjectAnalytics(
        String subjectName,
        String colorHex,
        long minutes,
        String minutesLabel
) {
}