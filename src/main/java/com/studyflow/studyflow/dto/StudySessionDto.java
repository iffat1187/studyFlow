package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.StudySessionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Data Transfer Object (DTO) for StudySession.
 *
 * This record is used to transfer data between the
 * Controller and Service layers without exposing
 * the Entity directly.
 *
 * Ownership is never part of the payload: the authenticated user is
 * resolved by the service layer, and the submitted subjectId/topicId are
 * only trusted after the service verifies they belong to that user.
 * {@code subjectName} and {@code topicName} are read-only display values.
 */
public record StudySessionDto(

        Long id,

        /**
         * Subject ID this session belongs to.
         * The subject is the authoritative scoping field and is required.
         */
        @NotNull(message = "Subject is required.")
        Long subjectId,

        /**
         * Optional topic ID this session focuses on.
         * A session may be logged at the subject level with no topic.
         */
        Long topicId,

        /**
         * Subject name for display (read-only).
         */
        String subjectName,

        /**
         * Topic name for display (read-only).
         */
        String topicName,

        /**
         * Session date is required.
         * Bound from the HTML date input (yyyy-MM-dd) using ISO formatting.
         */
        @NotNull(message = "Session date is required.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate sessionDate,

        /**
         * Optional session start time.
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime startTime,

        /**
         * Optional session end time.
         */
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime endTime,

        /**
         * Session duration in minutes is required.
         * Stored numerically; a display form like "1h 30m" may be derived
         * from this value by the UI, never stored as a string.
         */
        @NotNull(message = "Session duration is required.")
        Integer durationMinutes,

        /**
         * Session type is required.
         */
        @NotNull(message = "Session type is required.")
        StudySessionType sessionType,

        /**
         * Optional free-form notes.
         */
        @Size(max = 500, message = "Notes cannot exceed 500 characters.")
        String notes

) {
}