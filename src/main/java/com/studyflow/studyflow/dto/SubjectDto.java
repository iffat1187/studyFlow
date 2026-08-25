package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for Subject.
 *
 * This record is used to transfer data between the
 * Controller and Service layers without exposing
 * the Entity directly.
 */
public record SubjectDto(

        Long id,

        /**
         * Subject name is required.
         */
        @NotBlank(message = "Subject name is required.")
        @Size(max = 100, message = "Subject name cannot exceed 100 characters.")
        String name,

        /**
         * Optional short description.
         */
        @Size(max = 200, message = "Description cannot exceed 200 characters.")
        String description,

        /**
         * Subject color is required.
         */
        @NotBlank(message = "Please select a subject color.")
        String color,

        /**
         * Number of topics in this subject (for display).
         */
        Long topicCount

) {
}