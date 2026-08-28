package com.studyflow.studyflow.entity;

/**
 * Enum representing the recurrence pattern of a task.
 *
 * Stored in the database as a String via EnumType.STRING
 * so the stored values remain stable even if the enum
 * definition is reordered.
 */
public enum RecurrenceType {

    /**
     * Task occurs only once on the specified due date.
     */
    ONCE,

    /**
     * Task repeats daily.
     */
    DAILY,

    /**
     * Task repeats weekly.
     */
    WEEKLY,

    /**
     * Task repeats monthly.
     */
    MONTHLY
}