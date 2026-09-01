package com.studyflow.studyflow.entity;

/**
 * Enum categorizing the purpose of a {@link Notification}.
 *
 * Stored in the database as a String via {@code EnumType.STRING} so the
 * stored values remain stable even if the enum is reordered. These are the
 * initial notification categories; automated generation rules that emit
 * them belong to a later sub-phase.
 */
public enum NotificationType {

    /**
     * A task is due (e.g. today or soon).
     */
    TASK_DUE,

    /**
     * A task has passed its due date without completion.
     */
    TASK_OVERDUE,

    /**
     * An exam is approaching soon.
     */
    EXAM_UPCOMING,

    /**
     * An exam is scheduled for today.
     */
    EXAM_TODAY,

    /**
     * A reminder to log study activity.
     */
    STUDY_REMINDER,

    /**
     * A reminder about a study streak at risk.
     */
    STREAK_REMINDER,

    /**
     * A generic system notification.
     */
    SYSTEM
}
