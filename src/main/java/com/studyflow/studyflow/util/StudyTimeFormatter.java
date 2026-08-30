package com.studyflow.studyflow.util;

/**
 * Shared display formatting for study durations.
 *
 * Durations are stored and aggregated in the database as plain minutes;
 * this formatter turns a minute count into the compact display form used
 * on the dashboard (and elsewhere in the UI):
 *
 *    0    → "0m"
 *    30   → "30m"
 *    60   → "1h"
 *    90   → "1h 30m"
 *    125  → "2h 5m"
 */
public final class StudyTimeFormatter {

    private StudyTimeFormatter() {
    }

    /**
     * Format a minute count as a compact human-readable duration.
     *
     * @param minutes the duration in minutes (0 or negative renders as "0m")
     * @return the display form, never null
     */
    public static String formatMinutes(long minutes) {
        if (minutes <= 0) {
            return "0m";
        }
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) {
            return remainder + "m";
        }
        if (remainder == 0) {
            return hours + "h";
        }
        return hours + "h " + remainder + "m";
    }
}