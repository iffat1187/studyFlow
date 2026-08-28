package com.studyflow.studyflow.entity;

/**
 * Enum representing the type/category of a task.
 *
 * Stored in the database as a String via EnumType.STRING
 * so the stored values remain stable even if the enum
 * definition is reordered.
 */
public enum TaskType {

    /**
     * Homework, assignment, problem set.
     */
    ASSIGNMENT,

    /**
     * Quiz or short test.
     */
    QUIZ,

    /**
     * Reminder for a deadline or event.
     */
    REMINDER,

    /**
     * Revision or review session.
     */
    REVISION,

    /**
     * Laboratory report.
     */
    LAB_REPORT,

    /**
     * Project or long-term assignment.
     */
    PROJECT,

    /**
     * Reading assignment.
     */
    READING,

    /**
     * Generic/other task type.
     */
    OTHER
}