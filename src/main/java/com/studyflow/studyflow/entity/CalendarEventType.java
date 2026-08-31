package com.studyflow.studyflow.entity;

/**
 * Enum representing the source entity type of a calendar event.
 *
 * Used by the calendar presentation layer to distinguish and visually
 * style events derived from different domain entities. This enum is NOT
 * stored in the database — calendar events are derived from existing
 * Task, Exam and StudySession records, not from a separate persistence
 * table.
 */
public enum CalendarEventType {

    /**
     * A study task with a due date and optional due time.
     */
    TASK,

    /**
     * An exam with a scheduled date and optional time.
     */
    EXAM,

    /**
     * A logged study session with a date and optional time range.
     */
    STUDY_SESSION
}
