package com.studyflow.studyflow.dto;

/**
 * One session type's study total for the analytics page.
 *
 * Computed from the real {@code StudySessionType} enum values grouped in
 * the database; {@code typeLabel} is a human-friendly display form of the
 * same enum, so no duplicate type system is introduced. {@code colorHex}
 * comes from the restrained StudyFlow chart palette (assigned by rank) so
 * the doughnut and its supporting list share one color per type.
 */
public record SessionTypeAnalytics(
        String typeLabel,
        long minutes,
        String minutesLabel,
        String colorHex
) {
}