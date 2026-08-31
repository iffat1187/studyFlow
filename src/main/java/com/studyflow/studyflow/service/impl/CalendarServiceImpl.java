package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.CalendarEventDto;
import com.studyflow.studyflow.entity.CalendarEventType;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.CalendarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implementation of {@link CalendarService}.
 *
 * The calendar is a pure aggregation layer backed by the real, user-scoped
 * Task, Exam and StudySession records — no new persistence model. Each
 * source repository's date-range query runs against the database (no
 * all-data fetch + in-memory filtering), every query is scoped to the
 * authenticated user through the subject chain, and the resulting DTOs are
 * combined and sorted chronologically by start date then start time.
 *
 * An unknown account (or one with no matching records) yields an empty
 * list rather than fabricated events.
 */
@Service
public class CalendarServiceImpl implements CalendarService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ExamRepository examRepository;
    private final StudySessionRepository studySessionRepository;

    /**
     * Constructor injection of the source repositories.
     */
    public CalendarServiceImpl(UserRepository userRepository,
                               TaskRepository taskRepository,
                               ExamRepository examRepository,
                               StudySessionRepository studySessionRepository) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.examRepository = examRepository;
        this.studySessionRepository = studySessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventDto> getCalendarEvents(String userEmail, LocalDate startDate, LocalDate endDate) {
        User user = resolveUser(userEmail);
        if (user == null || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }

        List<CalendarEventDto> events = new ArrayList<>();
        events.addAll(taskRepository.findBySubject_UserAndDueDateBetween(user, startDate, endDate)
                .stream().map(this::toEvent).toList());
        events.addAll(examRepository.findBySubject_UserAndExamDateBetween(user, startDate, endDate)
                .stream().map(this::toEvent).toList());
        events.addAll(studySessionRepository.findBySubject_UserAndSessionDateBetween(user, startDate, endDate)
                .stream().map(this::toEvent).toList());

        return events.stream()
                .sorted(Comparator
                        .comparing(CalendarEventDto::startDate)
                        .thenComparing(event -> nullSafeTime(event.startTime()))
                        .thenComparing(CalendarEventDto::eventType)
                        .thenComparing(CalendarEventDto::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Map a {@link Task} to a calendar event. Completed/overdue status is
     * derived from the real fields.
     */
    private CalendarEventDto toEvent(Task task) {
        LocalDate today = LocalDate.now();
        int progress = task.getProgress() == null ? 0 : task.getProgress();
        boolean completed = Boolean.TRUE.equals(task.getCompleted());
        String status = completed ? "completed"
                : (task.getDueDate().isBefore(today) ? "overdue" : "pending");
        return new CalendarEventDto(
                task.getId(),
                task.getTitle(),
                CalendarEventType.TASK,
                task.getSubject().getId(),
                task.getDueDate(),
                task.getDueTime(),
                task.getDueDate(),
                null,
                task.getSubject().getName(),
                task.getSubject().getColor(),
                null,
                null,
                taskTypeLabel(task.getType()),
                null,
                progress,
                completed,
                status,
                "Task",
                null
        );
    }

    /**
     * Map an {@link Exam} to a calendar event.
     */
    private CalendarEventDto toEvent(Exam exam) {
        LocalDate today = LocalDate.now();
        String status = exam.getExamDate().isBefore(today)
                ? "past"
                : (exam.getExamDate().isEqual(today) ? "today" : "upcoming");
        return new CalendarEventDto(
                exam.getId(),
                exam.getName(),
                CalendarEventType.EXAM,
                exam.getSubject().getId(),
                exam.getExamDate(),
                exam.getExamTime(),
                exam.getExamDate(),
                null,
                exam.getSubject().getName(),
                exam.getSubject().getColor(),
                null,
                null,
                examTypeLabel(exam.getExamType()),
                null,
                null,
                false,
                status,
                "Exam",
                exam.getDurationMinutes()
        );
    }

    /**
     * Map a {@link StudySession} to a calendar event. The title is built
     * from the subject name and session type label; the topic name is
     * carried through when present.
     */
    private CalendarEventDto toEvent(StudySession session) {
        String topicName = session.getTopic() == null ? null : session.getTopic().getName();
        String title = session.getSubject().getName() + " — " + sessionTypeLabel(session.getSessionType());
        return new CalendarEventDto(
                session.getId(),
                title,
                CalendarEventType.STUDY_SESSION,
                session.getSubject().getId(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getSessionDate(),
                session.getEndTime(),
                session.getSubject().getName(),
                session.getSubject().getColor(),
                topicName,
                sessionTypeLabel(session.getSessionType()),
                null,
                session.getNotes(),
                null,
                false,
                "logged",
                "Study Session",
                session.getDurationMinutes()
        );
    }

    /**
     * Human-friendly display form of a {@code TaskType} enum, e.g.
     * LAB_REPORT → "Lab Report".
     */
    private String taskTypeLabel(TaskType type) {
        if (type == null) {
            return null;
        }
        return humanize(type.name());
    }

    /**
     * Human-friendly display form of a {@code ExamType} enum, e.g.
     * MIDTERM → "Midterm", CLASS_TEST → "Class Test".
     */
    private String examTypeLabel(ExamType type) {
        if (type == null) {
            return null;
        }
        return humanize(type.name());
    }

    /**
     * Human-friendly display form of a {@code SessionType} enum, e.g.
     * STUDY_SESSION → "Study Session", EXAM_PREPARATION → "Exam Preparation".
     */
    private String sessionTypeLabel(StudySessionType type) {
        if (type == null) {
            return "Study Session";
        }
        return humanize(type.name());
    }

    /**
     * Convert an underscore-separated uppercase enum name into Title Case
     * words, e.g. CLASS_TEST → "Class Test".
     */
    private String humanize(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ENGLISH).split("_"))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Resolve the authenticated user record, or null when it cannot be found.
     */
    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }

    /**
     * Null-safe start-time comparator fallback: events without a time sort
     * before timed events on the same date.
     */
    private LocalTime nullSafeTime(LocalTime time) {
        return time == null ? LocalTime.MIN : time;
    }
}
