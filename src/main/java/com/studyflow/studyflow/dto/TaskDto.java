package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.RecurrenceType;
import com.studyflow.studyflow.entity.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Data Transfer Object (DTO) for Task.
 *
 * This record is used to transfer data between the
 * Controller and Service layers without exposing
 * the Entity directly.
 */
public record TaskDto(

        Long id,

        /**
         * Subject ID this task belongs to.
         */
        Long subjectId,

        /**
         * Subject name for display (read-only).
         */
        String subjectName,

        /**
         * Task title is required.
         */
        @NotBlank(message = "Task title is required.")
        @Size(max = 100, message = "Task title cannot exceed 100 characters.")
        String title,

        /**
         * Optional details/notes.
         */
        @Size(max = 500, message = "Details cannot exceed 500 characters.")
        String details,

        /**
         * Task type is required.
         */
        @NotNull(message = "Task type is required.")
        TaskType type,

        /**
         * Due date is required.
         */
        @NotNull(message = "Due date is required.")
        LocalDate dueDate,

        /**
         * Optional due time.
         */
        LocalTime dueTime,

        /**
         * Recurrence type is required.
         */
        @NotNull(message = "Recurrence type is required.")
        RecurrenceType recurrenceType,

        /**
         * Progress percentage (0-100).
         */
        Integer progress,

        /**
         * Completion status.
         */
        Boolean completed

) {
}