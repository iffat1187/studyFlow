package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for Topic.
 *
 * This record is used to transfer data between the
 * Controller and Service layers without exposing
 * the Entity directly.
 */
public record TopicDto(

        Long id,

        /**
         * Subject ID this topic belongs to.
         */
        Long subjectId,

        /**
         * Subject name for display (read-only).
         */
        String subjectName,

        /**
         * Topic name is required.
         */
        @NotBlank(message = "Topic name is required.")
        @Size(max = 100, message = "Topic name cannot exceed 100 characters.")
        String name,

        /**
         * Optional short description.
         */
        @Size(max = 200, message = "Description cannot exceed 200 characters.")
        String description

) {
}