package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity class representing a study session within a subject.
 * Maps to the "study_sessions" table in the database.
 *
 * Every study session belongs to exactly one {@link Subject}. Ownership is
 * enforced through the subject's owner: a session can never be read or
 * modified through another user's account because all queries are scoped
 * to the authenticated user's subjects.
 *
 * The intended ownership chain is:
 *
 *     StudySession
 *         ↓
 *     Subject
 *         ↓
 *     User
 *
 * A session may optionally reference a {@link Topic} within the same
 * subject. The relationship is NOT cascade-delete configured: subject and
 * topic lifecycle is managed explicitly to avoid unintended data loss.
 */
@Entity
@Table(name = "study_sessions")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class StudySession {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type/category of the study session.
     * Cannot be null in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private StudySessionType sessionType;

    /**
     * Date the study session is scheduled for.
     * Cannot be null in the database.
     */
    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /**
     * Optional start time of the session.
     * Can be null if the session has no fixed start time.
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * Optional end time of the session.
     * Can be null if the session has no fixed end time.
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Duration of the session in minutes.
     * Cannot be null in the database.
     */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /**
     * Optional free-form notes about the session.
     * Maximum length is 500 characters.
     */
    @Column(length = 500)
    private String notes;

    /**
     * Timestamp when the session was created.
     * Set automatically by JPA on first persist.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * The subject this study session belongs to.
     *
     * LAZY loading avoids pulling the subject when it is not needed.
     * The column is nullable in the database so pre-existing rows created
     * before the relationship existed remain valid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    /**
     * The optional topic this study session focuses on.
     *
     * LAZY loading avoids pulling the topic when it is not needed.
     * Nullable, so a session can be logged at the subject level without
     * picking a specific topic.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

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