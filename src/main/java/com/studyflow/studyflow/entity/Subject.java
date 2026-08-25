package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity class representing a study subject.
 * Maps to the "subjects" table in the database.
 *
 * Every subject belongs to exactly one {@link User}. Ownership is enforced
 * by the service layer through user-scoped repository lookups, so a
 * subject can never be read or modified through another user's account.
 *
 * A subject can contain many {@link Topic} entries. The relationship is
 * bidirectional via the {@code subject} field in Topic. Cascade is NOT
 * configured for ALL to avoid unintended bulk deletions; topic lifecycle
 * is managed explicitly.
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
public class Subject {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the subject.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Short description about the subject.
     * Maximum length is 200 characters.
     */
    @Column(length = 200)
    private String description;

    /**
     * Color used for displaying the subject in the UI.
     * Example: Blue, Green, #3B82F6
     */
    @Column(nullable = false)
    private String color;

    /**
     * The user who owns this subject.
     *
     * LAZY loading avoids pulling the owner when it is not needed. The
     * column stays nullable in the database so pre-existing rows created
     * before the relationship existed remain valid (they are simply not
     * visible to any user until assigned an owner).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * Topics belonging to this subject.
     *
     * LAZY loading avoids pulling all topics when only the subject is needed.
     * The owning side of the relationship is {@link Topic#getSubject()}.
     * No cascade is configured: deleting a subject does NOT automatically
     * delete its topics. Topic lifecycle is managed explicitly to prevent
     * unintended data loss. The service layer handles subject deletion
     * with appropriate checks or explicit topic removal.
     */
    @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY)
    private List<Topic> topics = new ArrayList<>();
}