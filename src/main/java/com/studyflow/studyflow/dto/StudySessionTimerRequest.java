package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.StudySessionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request payload for the Focus Timer → Study Session persistence endpoint
 * (POST /study-sessions/from-timer).
 *
 * The timer sends only the data needed to construct a normal
 * {@link StudySession}: the selected subject/topic/type, the wall-clock
 * start and end times, the rounded active-study duration, and an optional
 * note. Ownership is NEVER part of the payload — the authenticated user is
 * resolved from Spring Security and the submitted ids are only trusted
 * after the service verifies Subject → User and Topic → Subject ownership.
 *
 * {@code clientKey} is a client-generated idempotency key (UUID). It is not
 * a security credential: it only lets the backend refuse a replayed
 * completion of the same timer run, preventing duplicate StudySessions.
 */
public record StudySessionTimerRequest(

        /**
         * Subject the session belongs to (required).
         * The subject is the authoritative scoping field.
         */
        @NotNull(message = "Subject is required.")
        Long subjectId,

        /**
         * Optional topic the session focused on (null = subject-level session).
         */
        Long topicId,

        /**
         * Session type, using the existing enum (NOT an arbitrary string).
         */
        @NotNull(message = "Session type is required.")
        StudySessionType sessionType,

        /**
         * Date of the study activity (the local date the timer started).
         */
        @NotNull(message = "Session date is required.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate sessionDate,

        /**
         * Real wall-clock start time of the session.
         */
        @NotNull(message = "Start time is required.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime startTime,

        /**
         * Real wall-clock end time of the session.
         */
        @NotNull(message = "End time is required.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime endTime,

        /**
         * Actual active study duration in minutes (paused time excluded),
         * rounded by the project rule. Never the raw selected preset.
         */
        @NotNull(message = "Session duration is required.")
        @Min(value = 1, message = "Duration must be at least 1 minute.")
        @Max(value = 1440, message = "Duration cannot exceed 1440 minutes.")
        Integer durationMinutes,

        /**
         * Optional free-form notes (the timer UI currently sends none).
         */
        @Size(max = 500, message = "Notes cannot exceed 500 characters.")
        String notes,

        /**
         * Client-generated idempotency key used to refuse duplicate saves of
         * the same timer run (double-click / completion firing twice).
         */
        @NotBlank(message = "Client key is required.")
        String clientKey

) {
}