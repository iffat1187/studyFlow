package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.NotificationPreference;
import com.studyflow.studyflow.entity.NotificationType;

/**
 * Data Transfer Object for {@link NotificationPreference}.
 *
 * Transfers the user's notification category preferences between the Service
 * and Controller/View layers without exposing the entity or the owning
 * {@code User}.
 *
 * The owner is intentionally NOT part of this record: ownership is resolved
 * server-side from the authenticated security context and never supplied by
 * the browser. No password or authentication data is ever exposed.
 */
public record NotificationPreferenceDto(

        /**
         * Task Due notifications (default true).
         */
        Boolean taskDueEnabled,

        /**
         * Task Overdue notifications (default true).
         */
        Boolean taskOverdueEnabled,

        /**
         * Exam Upcoming notifications (default true).
         */
        Boolean examUpcomingEnabled,

        /**
         * Exam Today notifications (default true).
         */
        Boolean examTodayEnabled,

        /**
         * Study Reminder notifications (default true).
         */
        Boolean studyReminderEnabled,

        /**
         * Streak Reminder notifications (default true).
         */
        Boolean streakReminderEnabled,

        /**
         * System notifications (default true).
         */
        Boolean systemEnabled

) {

    /**
     * Static factory from the entity, kept in the DTO for symmetry with the
     * module's record-based DTOs. The owner is never carried across.
     *
     * @param entity the preference entity
     * @return a copy-safe DTO
     */
    public static NotificationPreferenceDto from(NotificationPreference entity) {
        if (entity == null) {
            return null;
        }
        return new NotificationPreferenceDto(
                entity.isTaskDueEnabled(),
                entity.isTaskOverdueEnabled(),
                entity.isExamUpcomingEnabled(),
                entity.isExamTodayEnabled(),
                entity.isStudyReminderEnabled(),
                entity.isStreakReminderEnabled(),
                entity.isSystemEnabled()
        );
    }

    /**
     * Normalized lookup: whether a category is enabled, defaulting to true
     * when the corresponding field is null. Used where a preference may not
     * have been supplied (e.g. an unchecked form toggle).
     *
     * @param type the notification category
     * @return true when enabled
     */
    public boolean enabledFor(NotificationType type) {
        if (type == null) {
            return true;
        }
        switch (type) {
            case TASK_DUE:
                return Boolean.TRUE.equals(taskDueEnabled);
            case TASK_OVERDUE:
                return Boolean.TRUE.equals(taskOverdueEnabled);
            case EXAM_UPCOMING:
                return Boolean.TRUE.equals(examUpcomingEnabled);
            case EXAM_TODAY:
                return Boolean.TRUE.equals(examTodayEnabled);
            case STUDY_REMINDER:
                return Boolean.TRUE.equals(studyReminderEnabled);
            case STREAK_REMINDER:
                return Boolean.TRUE.equals(streakReminderEnabled);
            case SYSTEM:
                return Boolean.TRUE.equals(systemEnabled);
            default:
                return true;
        }
    }
}
