package com.studyflow.studyflow.dto;

/**
 * Current study streak for the authenticated user.
 *
 * Derived from the user's real active study dates (days with at least one
 * positive-duration session). {@code days} is the number of consecutive
 * active days ending today (or yesterday when today has no activity yet,
 * so a streak is never silently broken mid-day). {@code activeToday} and
 * {@code hasRecentActivity} describe the current state so the template can
 * show honest copy instead of inventing momentum.
 */
public record StudyStreak(
        long days,
        boolean activeToday,
        boolean hasRecentActivity
) {
}