package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.CalendarEventType;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read-only view of a calendar event for the unified calendar.
 *
 * Calendar events are NOT persisted. This DTO is derived at runtime from
 * existing domain records: {@link com.studyflow.studyflow.entity.Task},
 * {@link com.studyflow.studyflow.entity.Exam} and
 * {@link com.studyflow.studyflow.entity.StudySession}.
 *
 * The calendar service (in a later sub-phase) will construct instances
 * of this record from real, user-scoped entities. Every field is
 * populated from the source entity — no data is fabricated.
 *
 * <h3>Field mapping by source entity</h3>
 * <pre>
 * ┌─────────────────┬───────────────────────────────────────────────┐
 * │ Field           │ TASK              │ EXAM              │ SESSION│
 * ├─────────────────┼───────────────────────────────────────────────┤
 * │ id              │ task.id           │ exam.id           │ ses.id │
 * │ title           │ task.title        │ exam.name         │ *1     │
 * │ eventType       │ TASK              │ EXAM              │ SESSION│
 * │ subjectId       │ subject.id        │ subject.id        │ sub.id │
 * │ startDate       │ task.dueDate      │ exam.examDate     │ sesDate│
 * │ startTime       │ task.dueTime?     │ exam.examTime?    │ start? │
 * │ endDate         │ task.dueDate      │ exam.examDate     │ sesDate│
 * │ endTime         │ null              │ null              │ end?   │
 * │ subjectName     │ subject.name      │ subject.name      │ subName│
 * │ subjectColor    │ subject.color     │ subject.color     │ subClr │
 * │ topicName       │ null              │ null              │ topic? │
 * │ sessionTypeLabel│ null              │ null              │ *3     │
 * │ subTypeLabel    │ task.type label   │ exam.examType lab.│ null   │
 * │ notes           │ null              │ null              │ ses.?? │
 * │ progress        │ task.progress     │ null              │ null   │
 * │ completed       │ task.completed    │ false             │ false  │
 * │ status          │ *2                │ "upcoming"/"today"│ "logged│
 * │ typeLabel       │ "Task"            │ "Exam"            │ "Study │
 * │                 │                   │                   │ Session│
 * │ durationMinutes │ null              │ exam.duration     │ sesDur │
 * └─────────────────┴───────────────────────────────────────────────┘
 *
 * *1 StudySession title = subjectName + " — " + sessionType label
 * *2 Task status = "completed" / "overdue" / "pending"
 * *3 StudySession type label (e.g. "Focus"), null for tasks/exams
 * </pre>
 *
 * <h3>Nullability</h3>
 * {@code startTime}, {@code endTime}, {@code topicName}, {@code progress},
 * {@code durationMinutes}, {@code subTypeLabel} and {@code sessionTypeLabel}
 * are nullable: tasks and exams without a time represent all-day events,
 * study sessions may lack a topic, only tasks carry progress, and the
 * sub-type labels vary by event kind (a task type / exam type, or null).
 * {@code notes} is populated only for study sessions.
 */
public record CalendarEventDto(
        Long id,
        String title,
        CalendarEventType eventType,
        Long subjectId,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime,
        String subjectName,
        String subjectColor,
        String topicName,
        String sessionTypeLabel,
        String subTypeLabel,
        String notes,
        Integer progress,
        boolean completed,
        String status,
        String typeLabel,
        Integer durationMinutes
) {
}
