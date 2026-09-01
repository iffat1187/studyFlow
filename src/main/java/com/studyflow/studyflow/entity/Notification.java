package com.studyflow.studyflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity class representing a notification belonging to a single user.
 * Maps to the "notifications" table in the database.
 *
 * Every notification belongs to exactly one {@link User}. Ownership is
 * the relationship's invariant: all retrieval is scoped to the owning
 * user through user-scoped repository queries, so a notification can
 * never be read or modified through another user's account (IDOR
 * prevention).
 *
 * The intended ownership chain is:
 *
 *     Notification
 *         +
 *     User
 *
 * No cascade is configured: deleting a user never cascades to their
 * notifications automatically, and notification lifecycle is managed
 * explicitly.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class Notification {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who owns this notification.
     *
     * LAZY loading avoids pulling the owner when it is not needed. The
     * nullable=false join column guarantees every notification has an owner.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Short headline for the notification.
     * Cannot be null in the database.
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Longer descriptive text. Maximum length is 1000 characters.
     */
    @Column(length = 1000)
    private String message;

    /**
     * Category of the notification.
     * Cannot be null and is stored as a String.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    /**
     * Whether the notification has been read by the user.
     * Defaults to false (unread) for newly created notifications.
     *
     * Maps to the "is_read" column because "read" is a reserved word in
     * MySQL and would otherwise break inserts and updates.
     */
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    /**
     * Deterministic deduplication key used to keep notification generation
     * idempotent (generating repeatedly must never produce duplicate
     * records for the same logical event).
     *
     * The key is derived server-side from: notification rule + source
     * entity id (for Task/Exam-driven rules) or the relevant date (for the
     * daily Study/Streak reminders). It is never presented to the user.
     *
     * Examples:
     *
     *     TASK_DUE:15          (task id 15)
     *     TASK_OVERDUE:15
     *     EXAM_UPCOMING:8
     *     EXAM_TODAY:8
     *     STUDY_REMINDER:2026-09-01
     *     STREAK_REMINDER:2026-09-01
     */
    @Column(name = "source_key", length = 100)
    private String sourceKey;

    /**
     * Creation timestamp.
     * Set automatically by JPA on first persist; the backend is the sole
     * source of truth for this value (never the browser).
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Populates the creation timestamp on insert.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
