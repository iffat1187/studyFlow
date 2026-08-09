package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity class representing an authenticated application user.
 * Maps to the "users" table in the database.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor // Required by JPA
@AllArgsConstructor // Creates a constructor with all fields
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
}