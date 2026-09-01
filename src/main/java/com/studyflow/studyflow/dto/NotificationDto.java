package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.NotificationType;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for {@link com.studyflow.studyflow.entity.Notification}.
 *
 * This record transfers notification data between the Service and Controller
 * layers without exposing the Entity or the owning {@code User} graph.
 *
 * The owner is intentionally NOT part of this record: ownership is resolved
 * server-side from the authenticated security context and is never supplied
 * by the browser. No password or authentication data is ever exposed.
 */
public record NotificationDto(

        /**
         * Notification primary key.
         */
        Long id,

        /**
         * Short headline text.
         */
        String title,

        /**
         * Longer descriptive text.
         */
        String message,

        /**
         * Category of the notification.
         */
        NotificationType type,

        /**
         * Read/unread state.
         */
        Boolean read,

        /**
         * Server-assigned creation timestamp.
         */
        LocalDateTime createdAt

) {

    /**
     * Static factory from the entity, kept in the DTO for symmetry with the
     * module's record-based DTOs. The owner is never carried across.
     *
     * @param entity the notification entity
     * @return a copy-safe DTO
     */
    public static NotificationDto from(com.studyflow.studyflow.entity.Notification entity) {
        if (entity == null) {
            return null;
        }
        return new NotificationDto(
                entity.getId(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getType(),
                entity.getRead(),
                entity.getCreatedAt()
        );
    }
}
