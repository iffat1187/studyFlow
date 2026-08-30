package com.studyflow.studyflow.dto;

import java.time.LocalDate;

/**
 * One recent study session shown on the dashboard.
 *
 * Derived from real, user-scoped sessions only. {@code topicName},
 * {@code subjectColor} and {@code durationLabel} are read-only display
 * values; {@code durationLabel} is pre-formatted so the template never
 * formats durations itself.
 */
public record RecentStudySession(
        Long id,
        String subjectName,
        String subjectColor,
        String topicName,
        LocalDate sessionDate,
        String durationLabel,
        String sessionTypeLabel
) {
}