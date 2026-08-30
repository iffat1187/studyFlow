package com.studyflow.studyflow.dto;

/**
 * One day of the current study-week breakdown shown on the dashboard.
 *
 * {@code minutes} is the raw aggregated value, {@code minutesLabel} the
 * pre-formatted display form and {@code barPercent} a 0-100 height for the
 * mini bar chart computed from the week's largest day. Days with no
 * activity render a 0% bar and a "0m" label — never a fabricated number.
 */
public record DayStudyMinutes(
        String dayName,
        int minutes,
        String minutesLabel,
        int barPercent,
        boolean isToday
) {
}