package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase C end-to-end persistence test (TEST 7).
 *
 * Uses the REAL repositories and a REAL database connection so the full
 * Controller -> Service -> Repository -> MySQL chain is exercised. The test
 * is transactional and rolls back afterwards, leaving the database clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExamPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void saveExam_validFlow_createsRowInDatabase() throws Exception {
        String email = "exam-it-" + System.nanoTime() + "@example.com";
        User user = User.builder()
                .name("IT User")
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
        user = userRepository.save(user);

        Subject subject = new Subject();
        subject.setName("Integrated Subject");
        subject.setDescription("Created by integration test");
        subject.setColor("Purple");
        subject.setUser(user);
        subject = subjectRepository.save(subject);

        mockMvc.perform(post("/exams/save")
                        .param("name", "Final Exam IT")
                        .param("subjectId", String.valueOf(subject.getId()))
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-15")
                        .param("examTime", "10:00")
                        .param("durationMinutes", "120")
                        .param("details", "Integration-test details")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"));

        List<Exam> exams = examRepository.findBySubject_UserOrderByExamDateAscExamTimeAscIdAsc(user);
        assertThat(exams).hasSize(1);
        Exam stored = exams.get(0);
        assertThat(stored.getName()).isEqualTo("Final Exam IT");
        assertThat(stored.getSubject().getId()).isEqualTo(subject.getId());
        assertThat(stored.getExamType()).isEqualTo(ExamType.FINAL);
        assertThat(stored.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(stored.getExamTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(stored.getDurationMinutes()).isEqualTo(120);
        assertThat(stored.getDetails()).isEqualTo("Integration-test details");
        assertThat(stored.getCreatedAt()).isNotNull();

        // The exam_type column stores the string name, never an ordinal integer.
        assertThat(stored.getExamType().name()).isEqualTo("FINAL");

        // The Exams list page (real Service/Repository/DB chain) renders it as an
        // upcoming exam card, proving the Db-scoped list query works end to end.
        mockMvc.perform(get("/exams")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Final Exam IT")));

        // A past exam belongs to the same user but must never appear in the
        // dashboard's Upcoming Exams section (DB-side date filtering).
        Exam pastExam = new Exam();
        pastExam.setName("Past Exam IT");
        pastExam.setExamType(ExamType.QUIZ);
        pastExam.setExamDate(LocalDate.of(2020, 1, 1));
        pastExam.setDurationMinutes(30);
        pastExam.setSubject(subject);
        examRepository.save(pastExam);

        // The dashboard (real chain) shows the upcoming exam and its "Next Exam"
        // highlight, but never the past one.
        mockMvc.perform(get("/dashboard")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Upcoming Exams")))
                .andExpect(content().string(containsString("Final Exam IT")))
                .andExpect(content().string(containsString("Next Exam")))
                .andExpect(content().string(not(containsString("Past Exam IT"))));

        Long examId = stored.getId();

        // TEST 1: the Edit form (real DB chain) pre-populates the existing values.
        mockMvc.perform(get("/exams/edit/{id}", examId)
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Exam")))
                .andExpect(content().string(containsString("Final Exam IT")));

        // TEST 2: updating via the real chain persists the changes.
        mockMvc.perform(post("/exams/update/{id}", examId)
                        .param("name", "Updated Exam IT")
                        .param("subjectId", String.valueOf(subject.getId()))
                        .param("examType", "MIDTERM")
                        .param("examDate", "2026-12-20")
                        .param("examTime", "14:30")
                        .param("durationMinutes", "90")
                        .param("details", "Edited via integration test")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"));

        Exam updated = examRepository.findById(examId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Exam IT");
        assertThat(updated.getSubject().getId()).isEqualTo(subject.getId());
        assertThat(updated.getExamType()).isEqualTo(ExamType.MIDTERM);
        assertThat(updated.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(updated.getExamTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(updated.getDurationMinutes()).isEqualTo(90);
        assertThat(updated.getDetails()).isEqualTo("Edited via integration test");

        // The list page reflects the update.
        mockMvc.perform(get("/exams")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Updated Exam IT")));

        // The dashboard reflects the edited values; the past exam stays hidden.
        mockMvc.perform(get("/dashboard")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Updated Exam IT")))
                .andExpect(content().string(containsString("Next Exam")))
                .andExpect(content().string(not(containsString("Past Exam IT"))));

        // TEST 4/6/8: a different user cannot update or delete this exam.
        String otherEmail = "other-it-" + System.nanoTime() + "@example.com";
        User other = User.builder()
                .name("Other IT")
                .email(otherEmail)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
        other = userRepository.save(other);

        mockMvc.perform(get("/exams/edit/{id}", examId)
                        .with(user(otherEmail).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/exams/update/{id}", examId)
                        .param("name", "Hijacked Exam")
                        .param("subjectId", String.valueOf(subject.getId()))
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-25")
                        .param("durationMinutes", "60")
                        .with(csrf())
                        .with(user(otherEmail).roles("USER")))
                .andExpect(status().isForbidden());

        // Unguarded GET delete was NOT used; delete is a POST and stays rejected.
        assertThat(examRepository.findById(examId).orElseThrow().getName())
                .isEqualTo("Updated Exam IT");

        mockMvc.perform(post("/exams/delete/{id}", examId)
                        .with(csrf())
                        .with(user(otherEmail).roles("USER")))
                .andExpect(status().isForbidden());

        assertThat(examRepository.findById(examId)).isPresent();

        // TEST 5: the owner deletes the exam through the real chain.
        mockMvc.perform(post("/exams/delete/{id}", examId)
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"));

        assertThat(examRepository.findById(examId)).isEmpty();

        // No stale data remains: the dashboard shows the honest empty state
        // after the exam is deleted.
        mockMvc.perform(get("/dashboard")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No upcoming exams.")))
                .andExpect(content().string(not(containsString("Updated Exam IT"))));
    }
}