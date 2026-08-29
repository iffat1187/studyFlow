package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.ExamType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Data Transfer Object (DTO) for Exam.
 *
 * This record is used to transfer data between the
 * Controller and Service layers without exposing
 * the Entity directly.
 */
public record ExamDto(

        Long id,

        /**
         * Subject ID this exam belongs to.
         * The subject is the authoritative selection and is required.
         */
        @NotNull(message = "Subject is required.")
        Long subjectId,

        /**
         * Subject name for display (read-only).
         */
        String subjectName,

        /**
         * Exam name is required.
         */
        @NotBlank(message = "Exam name is required.")
        @Size(max = 100, message = "Exam name cannot exceed 100 characters.")
        String name,

        /**
         * Exam type is required.
         */
        @NotNull(message = "Exam type is required.")
        ExamType examType,

        /**
         * Exam date is required.
         * Bound from the HTML date input (yyyy-MM-dd) using ISO formatting.
         */
        @NotNull(message = "Exam date is required.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate examDate,

        /**
         * Optional exam time.
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime examTime,

        /**
         * Exam duration in minutes is required.
         */
        @NotNull(message = "Exam duration is required.")
        Integer durationMinutes,

        /**
         * Optional details/notes.
         */
        @Size(max = 500, message = "Details cannot exceed 500 characters.")
        String details

) {
}