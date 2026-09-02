package com.studyflow.studyflow.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A single task rendered on the user dashboard.
 *
 * Status is derived in the transaction from the real entity state:
 * overdue, due-today, due-tomorrow, or future. Template and JS
 * read only this DTO — they never touch the entity directly.
 */
public record DashboardTaskItem(
        Long id,
        String title,
        String subjectName,
        String subjectColor,
        Long subjectId,
        String taskTypeName,
        LocalDate dueDate,
        LocalTime dueTime,
        Integer progress,
        boolean completed,
        boolean overdue,
        boolean dueToday,
        boolean dueTomorrow,
        String dueDateLabel,
        String dueTimeLabel,
        String statusLabel,
        boolean completedInSession
) {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    public static DashboardTaskItem fromEntity(
            com.studyflow.studyflow.entity.Task task,
            LocalDate today) {

        LocalDate due   = task.getDueDate();
        LocalTime time  = task.getDueTime();
        boolean done    = Boolean.TRUE.equals(task.getCompleted());

        boolean overdue   = !done && due.isBefore(today);
        boolean dueToday  = !done && due.equals(today);
        boolean dueTomorrow = !done && due.equals(today.plusDays(1));

        String dateLabel;
        if (dueToday)  dateLabel = "Today";
        else if (dueTomorrow) dateLabel = "Tomorrow";
        else           dateLabel = due.format(DATE_FMT);

        String timeLabel = (time != null) ? time.format(TIME_FMT) : null;

        String statusLabel;
        if (overdue && !done) {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(due, today);
            statusLabel = daysOverdue == 1
                    ? "1 day overdue"
                    : daysOverdue + " days overdue";
        } else if (dueToday) {
            statusLabel = "Due today";
        } else if (dueTomorrow) {
            statusLabel = "Due tomorrow";
        } else {
            statusLabel = null;
        }

        return new DashboardTaskItem(
                task.getId(),
                task.getTitle(),
                task.getSubject() != null ? task.getSubject().getName() : "",
                task.getSubject() != null ? task.getSubject().getColor() : "#6B7280",
                task.getSubject() != null ? task.getSubject().getId() : null,
                task.getType() != null ? task.getType().name().replace('_', ' ') : "Task",
                due,
                time,
                task.getProgress() != null ? task.getProgress() : 0,
                done,
                overdue,
                dueToday,
                dueTomorrow,
                dateLabel,
                timeLabel,
                statusLabel,
                done
        );
    }
}