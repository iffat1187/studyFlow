package com.studyflow.studyflow.security;

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
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.CalendarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-Phase B+C integration tests: every calendar aggregation query executes
 * against the real database (no stubs) using the same user-scoped JPQL the
 * service uses at runtime. Records are seeded inside a transaction and rolled
 * back, so nothing persists.
 *
 * Verifies that tasks, exams and study sessions are aggregated into the
 * unified calendar view, sorted chronologically, confined to the requested
 * date range and isolated per user (IDOR prevention).
 */
@SpringBootTest
@Transactional
class CalendarQueryIntegrationTest {

    private static final String ALICE_EMAIL = "c-alice@example.com";
    private static final String BOB_EMAIL = "c-bob@example.com";

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private Subject saveSubject(User user, String name, String color) {
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor(color);
        subject.setUser(user);
        return subjectRepository.save(subject);
    }

    private Task saveTask(Subject subject, String title, LocalDate due, LocalTime time) {
        Task task = new Task();
        task.setTitle(title);
        task.setSubject(subject);
        task.setType(TaskType.ASSIGNMENT);
        task.setDueDate(due);
        task.setDueTime(time);
        return taskRepository.save(task);
    }

    private Exam saveExam(Subject subject, String name, LocalDate date, LocalTime time, int duration) {
        Exam exam = new Exam();
        exam.setName(name);
        exam.setSubject(subject);
        exam.setExamType(ExamType.MIDTERM);
        exam.setExamDate(date);
        exam.setExamTime(time);
        exam.setDurationMinutes(duration);
        return examRepository.save(exam);
    }

    private StudySession saveSession(Subject subject, LocalDate date, LocalTime start, int duration) {
        StudySession session = new StudySession();
        session.setSubject(subject);
        session.setSessionType(StudySessionType.REVISION);
        session.setSessionDate(date);
        session.setStartTime(start);
        session.setDurationMinutes(duration);
        return studySessionRepository.save(session);
    }

    @Test
    void calendar_aggregatesAllSourcesInRangeSorted() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        User alice = saveUser(ALICE_EMAIL);
        Subject math = saveSubject(alice, "Math", "Blue");
        Subject science = saveSubject(alice, "Science", "Green");

        saveTask(math, "Homework 2", start.plusDays(10), LocalTime.of(10, 0));
        saveExam(science, "Physics Final", start.plusDays(5), LocalTime.of(9, 0), 180);
        saveSession(math, start.plusDays(20), LocalTime.of(14, 0), 90);
        // Outside the range: must be excluded.
        saveTask(math, "Outside Task", end.plusDays(3), null);

        List<CalendarEventDto> events = calendarService.getCalendarEvents(ALICE_EMAIL, start, end);

        assertEquals(3, events.size(), "only the three in-range events should appear");
        assertTrue(events.stream().noneMatch(e -> e.title().equals("Outside Task")));

        // Chronological: exam (Jun 6), task (Jun 11), session (Jun 21).
        assertEquals(CalendarEventType.EXAM, events.get(0).eventType());
        assertEquals(CalendarEventType.TASK, events.get(1).eventType());
        assertEquals(CalendarEventType.STUDY_SESSION, events.get(2).eventType());
    }

    @Test
    void calendar_isolatesUsers() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        User alice = saveUser(ALICE_EMAIL);
        User bob = saveUser(BOB_EMAIL);
        Subject mathAlice = saveSubject(alice, "Math A", "Blue");
        Subject mathBob = saveSubject(bob, "Math B", "Blue");

        saveTask(mathAlice, "Alice Task", start.plusDays(2), null);
        saveTask(mathBob, "Bob Task", start.plusDays(2), null);

        List<CalendarEventDto> aliceEvents = calendarService.getCalendarEvents(ALICE_EMAIL, start, end);
        List<CalendarEventDto> bobEvents = calendarService.getCalendarEvents(BOB_EMAIL, start, end);

        assertEquals(1, aliceEvents.size());
        assertEquals("Alice Task", aliceEvents.get(0).title());
        assertEquals(1, bobEvents.size());
        assertEquals("Bob Task", bobEvents.get(0).title());
    }

    @Test
    void calendar_unknownUserOrEmptyRange_yieldsEmpty() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        assertTrue(calendarService.getCalendarEvents("ghost@example.com", start, end).isEmpty());
        assertTrue(calendarService.getCalendarEvents(ALICE_EMAIL, end, start).isEmpty());
    }
}
