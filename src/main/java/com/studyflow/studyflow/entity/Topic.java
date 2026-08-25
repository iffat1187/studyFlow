package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity class representing a study topic within a subject.
 * Maps to the "topics" table in the database.
 *
 * Every topic belongs to exactly one {@link Subject}. Ownership is enforced
 * through the subject's owner: a topic can never be read or modified
 * through another user's account because all queries are scoped to the
 * authenticated user's subjects.
 */
@Entity
@Table(name = "topics")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class Topic {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the topic.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Short description about the topic.
     * Maximum length is 200 characters.
     */
    @Column(length = 200)
    private String description;

    /**
     * The subject this topic belongs to.
     *
     * LAZY loading avoids pulling the subject when it is not needed.
     * The column is nullable in the database so pre-existing rows created
     * before the relationship existed remain valid.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;
}