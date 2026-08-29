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
 * Entity class representing an exam within a subject.
 * Maps to the "exams" table in the database.
 *
 * Every exam belongs to exactly one {@link Subject}. Ownership is enforced
 * through the subject's owner: an exam can never be read or modified
 * through another user's account because all queries are scoped to the
 * authenticated user's subjects.
 */
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class Exam {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the exam.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Type/category of the exam.
     * Cannot be null in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamType examType;

    /**
     * Date the exam is scheduled for.
     * Cannot be null in the database.
     */
    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    /**
     * Time the exam is scheduled for.
     * Can be null if the time is not specified.
     */
    @Column(name = "exam_time")
    private LocalTime examTime;

    /**
     * Duration of the exam in minutes.
     * Cannot be null in the database.
     */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /**
     * Optional details/notes about the exam.
     * Maximum length is 500 characters.
     */
    @Column(length = 500)
    private String details;

    /**
     * Timestamp when the exam was created.
     * Set automatically by JPA on first persist.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * The subject this exam belongs to.
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