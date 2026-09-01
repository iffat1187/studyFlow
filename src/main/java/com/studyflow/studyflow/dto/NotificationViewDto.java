package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * Presentation-level view of a {@link NotificationDto} for the notification
 * center UI.
 *
 * Enriches the service DTO with the presentation details needed to render a
 * notification without putting business/layout rules in Thymeleaf or
 * JavaScript: the Bootstrap icon, the human-friendly category label and the
 * destination module href.
 *
 * The owner id and any internal source references are never exposed here.
 * {@code href} is null for notifications that should not navigate (SYSTEM).
 */
public record NotificationViewDto(

        Long id,

        String title,

        String message,

        NotificationType type,

        Boolean read,

        LocalDateTime createdAt,

        /**
         * Bootstrap Icons class (without the "bi " prefix), e.g. "bi-bell".
         */
        String icon,

        /**
         * Short human-friendly category label, e.g. "Task", "Exam", "Study".
         */
        String typeLabel,

        /**
         * Destination module href when the notification should navigate on
         * click; null when it should not (SYSTEM).
         */
        String href

) {

    /**
     * Build a view DTO from a service DTO, applying the single
     * type-to-icon/label/href mapping used across the bell, dropdown and
     * notification page so no duplicate mapping exists anywhere else.
     *
     * @param dto the service DTO (never null)
     * @return the enriched view DTO
     */
    public static NotificationViewDto from(NotificationDto dto) {
        if (dto == null) {
            return null;
        }
        return new NotificationViewDto(
                dto.id(),
                dto.title(),
                dto.message(),
                dto.type(),
                dto.read(),
                dto.createdAt(),
                iconFor(dto.type()),
                labelFor(dto.type()),
                hrefFor(dto.type())
        );
    }

    private static String iconFor(NotificationType type) {
        if (type == null) {
            return "bi-info-circle";
        }
        switch (type) {
            case TASK_DUE:
                return "bi-check2-square";
            case TASK_OVERDUE:
                return "bi-exclamation-triangle";
            case EXAM_UPCOMING:
                return "bi-mortarboard";
            case EXAM_TODAY:
                return "bi-calendar-event";
            case STUDY_REMINDER:
                return "bi-stopwatch";
            case STREAK_REMINDER:
                return "bi-fire";
            default:
                return "bi-info-circle";
        }
    }

    private static String labelFor(NotificationType type) {
        if (type == null) {
            return "System";
        }
        switch (type) {
            case TASK_DUE:
            case TASK_OVERDUE:
                return "Task";
            case EXAM_UPCOMING:
            case EXAM_TODAY:
                return "Exam";
            case STUDY_REMINDER:
                return "Study";
            case STREAK_REMINDER:
                return "Streak";
            default:
                return "System";
        }
    }

    private static String hrefFor(NotificationType type) {
        if (type == null || type == NotificationType.SYSTEM) {
            return null;
        }
        switch (type) {
            case TASK_DUE:
            case TASK_OVERDUE:
                return "/tasks";
            case EXAM_UPCOMING:
            case EXAM_TODAY:
                return "/exams";
            case STUDY_REMINDER:
            case STREAK_REMINDER:
                return "/study-sessions";
            default:
                return null;
        }
    }
}
