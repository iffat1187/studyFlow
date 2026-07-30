package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity class representing a study subject.
 * Maps to the "subjects" table in the database.
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
}