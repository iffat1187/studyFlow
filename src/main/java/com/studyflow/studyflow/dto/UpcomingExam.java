package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.ExamType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read-only view of an upcoming exam rendered on the user dashboard.
 *
 * The urgency fields (daysLeft, isToday, urgencyLabel) are computed
 * server-side from the real exam date, so the server clock remains the
 * single source of truth — no client-side countdown, no hard-coded values.
 */
public record UpcomingExam(
        Long id,
        String name,
        String subjectName,
        ExamType examType,
        LocalDate examDate,
        LocalTime examTime,
        Integer durationMinutes,
        long daysLeft,
        boolean isToday,
        String urgencyLabel
) {
}