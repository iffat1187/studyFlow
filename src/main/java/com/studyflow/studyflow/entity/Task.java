package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Entity class representing a study task within a subject.
 * Maps to the "tasks" table in the database.
 *
 * Every task belongs to exactly one {@link Subject}. Ownership is enforced
 * through the subject's owner: a task can never be read or modified
 * through another user's account because all queries are scoped to the
 * authenticated user's subjects.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class Task {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Title of the task.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String title;

    /**
     * Optional details/notes about the task.
     * Maximum length is 500 characters.
     */
    @Column(length = 500)
    private String details;

    /**
     * Type/category of the task.
     * Cannot be null in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    /**
     * Due date for the task.
     * Cannot be null in the database.
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /**
     * Optional due time for the task.
     * Can be null if the task is all-day or time is not specified.
     */
    @Column(name = "due_time")
    private LocalTime dueTime;

    /**
     * Recurrence pattern for the task.
     * Cannot be null in the database, defaults to ONCE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false)
    private RecurrenceType recurrenceType = RecurrenceType.ONCE;

    /**
     * Progress percentage (0-100).
     * Cannot be null in the database, defaults to 0.
     */
    @Column(nullable = false)
    private Integer progress = 0;

    /**
     * Completion status.
     * Cannot be null in the database, defaults to false.
     */
    @Column(nullable = false)
    private Boolean completed = false;

    /**
     * Timestamp when the task was created.
     * Set automatically by JPA on first persist.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * The subject this task belongs to.
     *
     * LAZY loading avoids pulling the subject when it is not needed.
     * The column is nullable in the database so pre-existing rows created
     * before the relationship existed remain valid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

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