package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.dto.NotificationViewDto;
import com.studyflow.studyflow.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the single type-to-icon/label/destination mapping used to
 * render notification items across the bell, the dropdown and the
 * notification page ({@link NotificationViewDto}).
 *
 * Ensures the mapping is complete for every NotificationType and that source
 * ids are never exposed, only safe module-level hrefs.
 */
class NotificationViewDtoTest {

    private NotificationDto dto(NotificationType type) {
        return new NotificationDto(1L, "Title", "Message", type, false,
                LocalDateTime.of(2026, 9, 1, 9, 0));
    }

    @Test
    void nullDto_mapsToNull() {
        assertThat(NotificationViewDto.from(null)).isNull();
    }

    @Test
    void taskDue_mapsToCheckIcon_tasksHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.TASK_DUE));
        assertThat(v.icon()).isEqualTo("bi-check2-square");
        assertThat(v.typeLabel()).isEqualTo("Task");
        assertThat(v.href()).isEqualTo("/tasks");
    }

    @Test
    void taskOverdue_mapsToWarningIcon_tasksHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.TASK_OVERDUE));
        assertThat(v.icon()).isEqualTo("bi-exclamation-triangle");
        assertThat(v.href()).isEqualTo("/tasks");
    }

    @Test
    void examUpcoming_mapsToMortarboard_examsHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.EXAM_UPCOMING));
        assertThat(v.icon()).isEqualTo("bi-mortarboard");
        assertThat(v.typeLabel()).isEqualTo("Exam");
        assertThat(v.href()).isEqualTo("/exams");
    }

    @Test
    void examToday_mapsToCalendarEvent_examsHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.EXAM_TODAY));
        assertThat(v.icon()).isEqualTo("bi-calendar-event");
        assertThat(v.href()).isEqualTo("/exams");
    }

    @Test
    void studyReminder_mapsToStopwatch_studySessionsHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.STUDY_REMINDER));
        assertThat(v.icon()).isEqualTo("bi-stopwatch");
        assertThat(v.typeLabel()).isEqualTo("Study");
        assertThat(v.href()).isEqualTo("/study-sessions");
    }

    @Test
    void streakReminder_mapsToFire_studySessionsHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.STREAK_REMINDER));
        assertThat(v.icon()).isEqualTo("bi-fire");
        assertThat(v.typeLabel()).isEqualTo("Streak");
        assertThat(v.href()).isEqualTo("/study-sessions");
    }

    @Test
    void system_mapsToInfoCircle_withNoHref() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.SYSTEM));
        assertThat(v.icon()).isEqualTo("bi-info-circle");
        assertThat(v.typeLabel()).isEqualTo("System");
        assertThat(v.href()).isNull();
    }

    @Test
    void viewDto_neverExposesSourceId() {
        NotificationViewDto v = NotificationViewDto.from(dto(NotificationType.TASK_DUE));
        // Only the module destination (a safe, general link) is exposed —
        // never any underlying task/exam/session id.
        assertThat(v.href()).isEqualTo("/tasks");
        for (Object val : new Object[]{v.id(), v.type(), v.href()}) {
            assertThat(String.valueOf(val)).doesNotContain("source");
        }
    }
}
