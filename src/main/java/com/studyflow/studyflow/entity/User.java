package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity class representing an authenticated application user.
 * Maps to the "users" table in the database.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Primary key.
     * Value is generated automatically by MySQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Display name of the user.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Email address used for login.
     * Cannot be null and must be unique in the database.
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Encoded password of the user.
     * Cannot be null in the database.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Role of the user.
     * Cannot be null and is stored as a String.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Account status. A disabled user cannot authenticate.
     *
     * New accounts are enabled by default. The DDL default keeps existing
     * rows enabled when the column is added to an already populated table.
     */
    @Builder.Default
    @Column(name = "enabled", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean enabled = true;

    /**
     * Registration timestamp of the user.
     * Set automatically by JPA on first persist.
     * Pre-existing rows are null until they have been re-saved.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Stored profile picture filename (e.g. a generated safe UUID-based name).
     *
     * Only the generated file name is stored — never the original uploaded
     * filename, never a filesystem path and never the image bytes. {@code null}
     * means the user has no profile picture and the default avatar is shown.
     * The physical file lives in the configured upload directory and is served
     * through the owner-scoped {@code /profile/picture/{filename}} endpoint.
     */
    @Column(name = "profile_image_path")
    private String profileImagePath;

    /**
     * Subjects owned by this user.
     *
     * The owning side of the relationship is {@link Subject#getUser()},
     * so this collection is populated from the inverse (mappedBy) side.
     * No cascade is configured: deleting a subject never deletes its user,
     * and deleting a user never cascades to their subjects.
     */
    @OneToMany(mappedBy = "user")
    private List<Subject> subjects = new ArrayList<>();

    /**
     * Populates the registration timestamp on insert.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}