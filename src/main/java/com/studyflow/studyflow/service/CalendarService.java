package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.CalendarEventDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Service that aggregates the authenticated user's data into a unified
 * calendar view.
 *
 * The calendar is a presentation/planning layer only: it NEVER persists
 * anything. Events are derived at runtime from existing, user-scoped
 * Domain records ({@link com.studyflow.studyflow.entity.Task},
 * {@link com.studyflow.studyflow.entity.Exam},
 * {@link com.studyflow.studyflow.entity.StudySession}) via each source
 * repository's date-range query, then combined and sorted chronologically.
 *
 * Every query is scoped to the authenticated user through the subject
 * chain, so one user can never observe another user's events (IDOR
 * prevention).
 */
public interface CalendarService {

    /**
     * Build the sorted list of calendar events for the authenticated user
     * whose start dates fall inside the given inclusive range.
     *
     * An unknown account (or one with no matching records) yields an empty
     * list rather than fabricated events.
     *
     * @param userEmail the authenticated user's email (from the security context)
     * @param startDate first date of the range (inclusive)
     * @param endDate   last date of the range (inclusive)
     * @return the user's events in the range, sorted by start date then start time
     */
    List<CalendarEventDto> getCalendarEvents(String userEmail, LocalDate startDate, LocalDate endDate);
}
