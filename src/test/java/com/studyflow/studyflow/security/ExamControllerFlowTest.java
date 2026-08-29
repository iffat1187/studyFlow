package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase C/D tests enforcing the Exam list + creation controller flow.
 *
 * Simulates User A and User B and verifies that the Exams page only exposes
 * the authenticated user's own exams and subjects, that every filter reaches
 * the user-scoped repository query, and that a submitted subject belonging to
 * another user is rejected without saving (IDOR prevention).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExamControllerFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private ExamRepository examRepository;

    private User buildUser(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    private User userA() {
        return buildUser(1L, "User A", "a@example.com");
    }

    private User userB() {
        return buildUser(2L, "User B", "b@example.com");
    }

    private Subject ownedSubject(Long id, String name, User owner) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setDescription("Owned by " + owner.getEmail());
        subject.setColor("Purple");
        subject.setUser(owner);
        return subject;
    }

    private Exam examEntity(Long id, String name, Subject subject, ExamType type,
                            LocalDate date, LocalTime time, int duration, String details) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setName(name);
        exam.setSubject(subject);
        exam.setExamType(type);
        exam.setExamDate(date);
        exam.setExamTime(time);
        exam.setDurationMinutes(duration);
        exam.setDetails(details);
        return exam;
    }

    private void stubOwnSubjects(User user, List<Subject> ownSubjects) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(ownSubjects);
    }

    /**
     * Mock the user-scoped upcoming/past queries used by the list page.
     */
    private void stubExamQueries(List<Exam> upcoming, List<Exam> past) {
        when(examRepository.findUpcoming(any(), any(), any(), any(), any()))
                .thenReturn(new ArrayList<>(upcoming));
        when(examRepository.findPast(any(), any(), any(), any(), any()))
                .thenReturn(new ArrayList<>(past));
    }

    @Test
    void getExams_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/exams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getExams_asUser_noExams_showsEmptyState() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of(ownedSubject(25L, "Spring Boot", a)));
        stubExamQueries(List.of(), List.of());

        mockMvc.perform(get("/exams")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("examDto"))
                .andExpect(model().attributeExists("subjects"))
                .andExpect(model().attribute("exams", org.hamcrest.Matchers.empty()))
                .andExpect(model().attribute("showExamModal", false))
                .andExpect(content().string(containsString("No exams yet")))
                .andExpect(content().string(containsString("Add First Exam")));
    }

    @Test
    void getExams_asUser_rendersOwnExamsAndOwnSubjectsOnly() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Subject subjectB = ownedSubject(30L, "B Subject", userB());
        // The modal subject dropdown must receive only User A's subjects.
        stubOwnSubjects(a, List.of(subjectA));
        stubExamQueries(List.of(examEntity(1L, "Spring Boot Final", subjectA, ExamType.FINAL,
                LocalDate.now().plusDays(20), LocalTime.of(10, 0), 120, "Open book")), List.of());

        mockMvc.perform(get("/exams")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentView", "upcoming"))
                .andExpect(content().string(containsString("Spring Boot Final")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("Final")))
                .andExpect(content().string(containsString("Upcoming")))
                .andExpect(content().string(containsString("Add Exam")))
                .andExpect(content().string(not(containsString("B Subject"))));

        // The exam list query is scoped to the authenticated user.
        verify(examRepository).findUpcoming(eq(a), eq(LocalDate.now()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void getExams_appliesSearchSubjectAndTypeFilters() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of(ownedSubject(25L, "Spring Boot", a)));
        stubExamQueries(List.of(), List.of());

        mockMvc.perform(get("/exams")
                        .param("search", "Spring")
                        .param("subjectId", "25")
                        .param("type", "MIDTERM")
                        .param("view", "upcoming")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchTerm", "Spring"))
                .andExpect(model().attribute("selectedSubjectId", 25L))
                .andExpect(model().attribute("selectedType", ExamType.MIDTERM))
                .andExpect(model().attribute("currentView", "upcoming"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<LocalDate> todayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> subjectCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<ExamType> typeCaptor = ArgumentCaptor.forClass(ExamType.class);

        verify(examRepository).findUpcoming(
                userCaptor.capture(), todayCaptor.capture(), searchCaptor.capture(),
                subjectCaptor.capture(), typeCaptor.capture());

        assertThat(userCaptor.getValue()).isSameAs(a);
        assertThat(todayCaptor.getValue()).isEqualTo(LocalDate.now());
        assertThat(searchCaptor.getValue()).isEqualTo("Spring");
        assertThat(subjectCaptor.getValue()).isEqualTo(25L);
        assertThat(typeCaptor.getValue()).isEqualTo(ExamType.MIDTERM);
    }

    @Test
    void getExams_pastView_usesPastQuery() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of(ownedSubject(25L, "Spring Boot", a)));
        stubExamQueries(List.of(), List.of());

        mockMvc.perform(get("/exams")
                        .param("view", "past")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentView", "past"));

        verify(examRepository).findPast(eq(a), eq(LocalDate.now()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void getExams_addParam_opensAddModalWithoutSubmitting() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of(ownedSubject(25L, "Spring Boot", a)));
        stubExamQueries(List.of(), List.of());

        // The dashboard's "+ Add Exam" deep-links to the real creation route
        // and pre-opens the modal; it must never auto-submit anything.
        mockMvc.perform(get("/exams")
                        .param("add", "true")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showExamModal", true))
                .andExpect(content().string(containsString("exam-modal-overlay open")));

        mockMvc.perform(get("/exams")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showExamModal", false))
                .andExpect(content().string(not(containsString("exam-modal-overlay open"))));
    }

    @Test
    void saveExam_validData_createsExamAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/exams/save")
                        .param("name", "Spring Boot Midterm")
                        .param("subjectId", "25")
                        .param("examType", "MIDTERM")
                        .param("examDate", "2026-12-15")
                        .param("examTime", "10:00")
                        .param("durationMinutes", "120")
                        .param("details", "Chapter 1-5")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"));

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());

        Exam saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Spring Boot Midterm");
        assertThat(saved.getSubject()).isSameAs(subjectA);
        assertThat(saved.getExamType()).isEqualTo(ExamType.MIDTERM);
        assertThat(saved.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(saved.getExamTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.getDurationMinutes()).isEqualTo(120);
        assertThat(saved.getDetails()).isEqualTo("Chapter 1-5");
    }

    @Test
    void saveExam_invalidData_returnsListWithModalOpenWithoutSaving() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        stubExamQueries(List.of(), List.of());

        mockMvc.perform(post("/exams/save")
                        .param("name", "")
                        .param("subjectId", "25")
                        .param("examType", "")
                        .param("examDate", "")
                        .param("durationMinutes", "")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showExamModal", true))
                .andExpect(model().attributeHasFieldErrors("examDto", "name", "examType", "examDate", "durationMinutes"))
                .andExpect(content().string(containsString("Add Exam")))
                .andExpect(content().string(containsString("Exam name is required.")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("exam-modal-overlay open")));

        verify(examRepository, never()).save(any());
    }

    @Test
    void saveExam_anotherUsersSubject_isRejected() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        // No subject id 30 belongs to User A (it belongs to User B).
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/exams/save")
                        .param("name", "Hijacked Exam")
                        .param("subjectId", "30")
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-15")
                        .param("examTime", "10:00")
                        .param("durationMinutes", "120")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(examRepository, never()).save(any());
    }

    @Test
    void saveExam_persistsFinalEnumNotInteger() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/exams/save")
                        .param("name", "Semester Final")
                        .param("subjectId", "25")
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-20")
                        .param("durationMinutes", "180")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"));

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());
        // Java enum stays typed; the STRING mapping persists "FINAL" (never an ordinal).
        assertThat(captor.getValue().getExamType()).isEqualTo(ExamType.FINAL);
        assertThat(captor.getValue().getExamType().name()).isEqualTo("FINAL");
    }

    @Test
    void getEdit_examOwnedByUser_rendersPrefilledForm() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(examRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(examEntity(50L, "Spring Boot Midterm", subjectA, ExamType.MIDTERM,
                        LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 120, "Chapter 1-5")));

        MvcResult result = mockMvc.perform(get("/exams/edit/50")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("examDto", "subjects"))
                .andExpect(content().string(containsString("Edit Exam")))
                .andExpect(content().string(containsString("Spring Boot Midterm")))
                .andExpect(content().string(containsString("value=\"2026-09-10\"")))
                .andExpect(content().string(containsString("name=\"examTime\" value=\"10:00")))
                .andExpect(content().string(containsString("Update Exam")))
                .andReturn();

        // The form is pre-populated from the user-owned exam (TEST 1).
        ExamDto dto = (ExamDto) result.getModelAndView().getModel().get("examDto");
        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.name()).isEqualTo("Spring Boot Midterm");
        assertThat(dto.subjectId()).isEqualTo(25L);
        assertThat(dto.examType()).isEqualTo(ExamType.MIDTERM);
        assertThat(dto.examDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(dto.examTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.durationMinutes()).isEqualTo(120);
        assertThat(dto.details()).isEqualTo("Chapter 1-5");

        // The exam was resolved through the user-scoped lookup only.
        verify(examRepository).findByIdAndSubject_User(eq(50L), any());
    }

    @Test
    void getEdit_anotherUsersExam_isForbiddenWithoutExposingData() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // Exam 50 belongs to User B: the user-scoped lookup returns nothing.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/exams/edit/50")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateExam_validData_updatesAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Subject calculus = ownedSubject(26L, "Calculus", a);
        Exam existing = examEntity(50L, "Spring Boot Midterm", subjectA, ExamType.MIDTERM,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 120, "Old notes");
        stubOwnSubjects(a, List.of(subjectA, calculus));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(existing));
        when(subjectRepository.findByIdAndUser(eq(26L), any())).thenReturn(Optional.of(calculus));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/exams/update/50")
                        .param("name", "Calculus Final Exam")
                        .param("subjectId", "26")
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-20")
                        .param("examTime", "14:00")
                        .param("durationMinutes", "180")
                        .param("details", "Updated details")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"))
                .andExpect(flash().attribute("successMessage", "Exam updated successfully!"));

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());

        Exam updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("Calculus Final Exam");
        // TEST 7: the exam moved to another subject owned by User A.
        assertThat(updated.getSubject()).isSameAs(calculus);
        assertThat(updated.getExamType()).isEqualTo(ExamType.FINAL);
        assertThat(updated.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(updated.getExamTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(updated.getDurationMinutes()).isEqualTo(180);
        assertThat(updated.getDetails()).isEqualTo("Updated details");
    }

    @Test
    void updateExam_invalidData_returnsEditFormWithoutSaving() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of(ownedSubject(25L, "Spring Boot", a)));

        mockMvc.perform(post("/exams/update/50")
                        .param("name", "Edited Exam")
                        .param("subjectId", "")
                        .param("examType", "FINAL")
                        .param("examDate", "")
                        .param("examTime", "10:00")
                        .param("durationMinutes", "120")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("examDto", "subjectId", "examDate"))
                .andExpect(content().string(containsString("Edit Exam")))
                .andExpect(content().string(containsString("Edited Exam")))
                .andExpect(content().string(containsString("Subject is required.")))
                .andExpect(content().string(containsString("Exam date is required.")));

        verify(examRepository, never()).save(any());
    }

    @Test
    void updateExam_anotherUsersExam_isForbidden() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // No exam id 50 belongs to User A.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/exams/update/50")
                        .param("name", "Hijacked Exam")
                        .param("subjectId", "25")
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-20")
                        .param("durationMinutes", "120")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(examRepository, never()).save(any());
    }

    @Test
    void updateExam_maliciousSubjectId_isRejected() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Exam existing = examEntity(50L, "Spring Boot Midterm", subjectA, ExamType.MIDTERM,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 120, "Notes");
        stubOwnSubjects(a, List.of(subjectA));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(existing));
        // Subject 30 belongs to User B: User A cannot move the exam onto it.
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/exams/update/50")
                        .param("name", "Moved Exam")
                        .param("subjectId", "30")
                        .param("examType", "FINAL")
                        .param("examDate", "2026-12-20")
                        .param("durationMinutes", "120")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(examRepository, never()).save(any());
    }

    @Test
    void deleteExam_ownsExam_deletesAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Exam exam = examEntity(50L, "Spring Boot Midterm", subjectA, ExamType.MIDTERM,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 120, "Notes");
        stubOwnSubjects(a, List.of(subjectA));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(exam));

        mockMvc.perform(post("/exams/delete/50")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exams"))
                .andExpect(flash().attribute("successMessage", "Exam deleted successfully!"));

        verify(examRepository).delete(exam);
    }

    @Test
    void deleteExam_anotherUsersExam_isForbiddenAndNotDeleted() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // No exam id 50 belongs to User A.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/exams/delete/50")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(examRepository, never()).delete(any());
    }

    @Test
    void editUpdateAndDeleteRoutes_anonymous_redirectToLogin() throws Exception {
        mockMvc.perform(get("/exams/edit/50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/exams/update/50").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/exams/delete/50").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}