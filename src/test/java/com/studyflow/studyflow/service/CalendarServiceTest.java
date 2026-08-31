package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.CalendarEventDto;
import com.studyflow.studyflow.entity.CalendarEventType;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.CalendarServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase B+C service-level tests.
 *
 * Exercises {@link CalendarServiceImpl} with mocked repositories so the
 * aggregation, mapping, chronological sorting and null/unknown handling can
 * be verified without a browser. The real SQL behind each repository method
 * is verified against the live database in {@code CalendarQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private StudySessionRepository studySessionRepository;

    @InjectMocks
    private CalendarServiceImpl calendarService;

    private User user;
    private LocalDate from;
    private LocalDate to;

    private Subject subject(String name, String color) {
        Subject s = new Subject();
        s.setName(name);
        s.setColor(color);
        return s;
    }

    private Topic topic(String name) {
        Topic t = new Topic();
        t.setName(name);
        return t;
    }

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).name("User").email("user@example.com")
                .password("encoded").role(Role.USER).build();
        from = LocalDate.of(2026, 8, 1);
        to = LocalDate.of(2026, 8, 31);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    private Task task(Long id, String title, Subject subject, LocalDate due, LocalTime dueTime,
                      int progress, boolean completed) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setSubject(subject);
        t.setDueDate(due);
        t.setDueTime(dueTime);
        t.setProgress(progress);
        t.setCompleted(completed);
        t.setType(TaskType.ASSIGNMENT);
        return t;
    }

    private Exam exam(Long id, String name, Subject subject, LocalDate date, LocalTime time,
                      int duration) {
        Exam e = new Exam();
        e.setId(id);
        e.setName(name);
        e.setSubject(subject);
        e.setExamDate(date);
        e.setExamTime(time);
        e.setDurationMinutes(duration);
        e.setExamType(ExamType.MIDTERM);
        return e;
    }

    private StudySession session(Long id, Subject subject, Topic topic, LocalDate date,
                                 LocalTime start, LocalTime end, int duration) {
        StudySession s = new StudySession();
        s.setId(id);
        s.setSubject(subject);
        s.setTopic(topic);
        s.setSessionType(StudySessionType.REVISION);
        s.setSessionDate(date);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setDurationMinutes(duration);
        return s;
    }

    @Test
    void events_aggregateAllSourcesSortedChronologically() {
        Subject math = subject("Math", "Blue");
        Subject science = subject("Science", "Green");

        Task mathTask = task(1L, "Homework 3", math, from.plusDays(2), LocalTime.of(10, 0), 50, false);
        Exam scienceExam = exam(2L, "Physics Midterm", science, from.plusDays(1), LocalTime.of(9, 0), 120);
        StudySession mathSession = session(3L, math, null, from.plusDays(2), LocalTime.of(14, 0),
                LocalTime.of(15, 30), 90);

        when(taskRepository.findBySubject_UserAndDueDateBetween(user, from, to))
                .thenReturn(List.of(mathTask));
        when(examRepository.findBySubject_UserAndExamDateBetween(user, from, to))
                .thenReturn(List.of(scienceExam));
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(user, from, to))
                .thenReturn(List.of(mathSession));

        List<CalendarEventDto> events = calendarService.getCalendarEvents("user@example.com", from, to);

        assertThat(events).hasSize(3);
        // Sorted by start date, then start time: exam (Aug 2 09:00), task (Aug 3 10:00), session (Aug 3 14:00)
        assertThat(events).extracting(CalendarEventDto::eventType)
                .containsExactly(CalendarEventType.EXAM, CalendarEventType.TASK, CalendarEventType.STUDY_SESSION);
    }

    @Test
    void taskEvent_mapsFieldsAndDerivesStatus() {
        Subject math = subject("Math", "Blue");
        Task completed = task(1L, "Done", math, LocalDate.now(), LocalTime.of(9, 0), 100, true);
        Task overdue = task(2L, "Late", math, LocalDate.now().minusDays(1), null, 0, false);

        when(taskRepository.findBySubject_UserAndDueDateBetween(user, from, to))
                .thenReturn(List.of(completed, overdue));
        when(examRepository.findBySubject_UserAndExamDateBetween(user, from, to)).thenReturn(List.of());
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(user, from, to)).thenReturn(List.of());

        List<CalendarEventDto> events = calendarService.getCalendarEvents("user@example.com", from, to);

        assertThat(events).hasSize(2);
        CalendarEventDto done = events.stream()
                .filter(e -> e.title().equals("Done")).findFirst().orElseThrow();
        assertThat(done.eventType()).isEqualTo(CalendarEventType.TASK);
        assertThat(done.subjectName()).isEqualTo("Math");
        assertThat(done.subjectColor()).isEqualTo("Blue");
        assertThat(done.progress()).isEqualTo(100);
        assertThat(done.completed()).isTrue();
        assertThat(done.status()).isEqualTo("completed");
        assertThat(done.typeLabel()).isEqualTo("Task");
        assertThat(done.durationMinutes()).isNull();

        CalendarEventDto late = events.stream()
                .filter(e -> e.title().equals("Late")).findFirst().orElseThrow();
        assertThat(late.status()).isEqualTo("overdue");
    }

    @Test
    void examEvent_mapsStatusByDate() {
        Subject science = subject("Science", "Green");
        LocalDate today = LocalDate.now();
        Exam upcoming = exam(1L, "Next", science, today.plusDays(5), null, 90);

        when(taskRepository.findBySubject_UserAndDueDateBetween(user, from, to)).thenReturn(List.of());
        when(examRepository.findBySubject_UserAndExamDateBetween(user, from, to)).thenReturn(List.of(upcoming));
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(user, from, to)).thenReturn(List.of());

        CalendarEventDto event = calendarService.getCalendarEvents("user@example.com", from, to).get(0);

        assertThat(event.eventType()).isEqualTo(CalendarEventType.EXAM);
        assertThat(event.status()).isEqualTo("upcoming");
        assertThat(event.typeLabel()).isEqualTo("Exam");
        assertThat(event.durationMinutes()).isEqualTo(90);
        assertThat(event.progress()).isNull();
        assertThat(event.completed()).isFalse();
    }

    @Test
    void sessionEvent_buildsTitleAndCarriesTopic() {
        Subject math = subject("Math", "Blue");
        Topic algebra = topic("Algebra");
        StudySession s = session(1L, math, algebra, from.plusDays(3), null, null, 60);

        when(taskRepository.findBySubject_UserAndDueDateBetween(user, from, to)).thenReturn(List.of());
        when(examRepository.findBySubject_UserAndExamDateBetween(user, from, to)).thenReturn(List.of());
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(user, from, to)).thenReturn(List.of(s));

        CalendarEventDto event = calendarService.getCalendarEvents("user@example.com", from, to).get(0);

        assertThat(event.eventType()).isEqualTo(CalendarEventType.STUDY_SESSION);
        assertThat(event.title()).isEqualTo("Math — Revision");
        assertThat(event.topicName()).isEqualTo("Algebra");
        assertThat(event.status()).isEqualTo("logged");
        assertThat(event.typeLabel()).isEqualTo("Study Session");
        assertThat(event.durationMinutes()).isEqualTo(60);
    }

    @Test
    void unknownUser_yieldsEmptyList() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        List<CalendarEventDto> events = calendarService.getCalendarEvents("ghost@example.com", from, to);

        assertThat(events).isEmpty();
    }

    @Test
    void nullOrBlankEmail_yieldsEmptyList() {
        assertThat(calendarService.getCalendarEvents(null, from, to)).isEmpty();
        assertThat(calendarService.getCalendarEvents("  ", from, to)).isEmpty();
    }

    @Test
    void emptyRange_orReversedRange_yieldsEmptyList() {
        assertThat(calendarService.getCalendarEvents("user@example.com", null, to)).isEmpty();
        assertThat(calendarService.getCalendarEvents("user@example.com", from, null)).isEmpty();
        assertThat(calendarService.getCalendarEvents("user@example.com", to, from)).isEmpty();
    }
}
