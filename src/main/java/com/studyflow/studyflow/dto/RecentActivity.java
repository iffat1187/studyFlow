package com.studyflow.studyflow.dto;

import java.time.LocalDate;

/**
 * One recent study activity entry on the analytics page.
 *
 * A small, newest-first preview only — the full history lives on the Study
 * Sessions page.
 */
public record RecentActivity(
        LocalDate sessionDate,
        String subjectName,
        String durationLabel
) {
}