package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ExamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 14 tests for the authenticated USER dashboard route /dashboard.
 *
 * Covers organization protection (anonymous redirect), the personalized
 * greeting from the authenticated principal, honest setup progress derived
 * from real data and the subject snapshot rendering.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private StudySessionRepository studySessionRepository;

    @MockitoBean
    private ExamService examService;

    private User buildUser(String name, String email) {
        return User.builder()
                .id(1L)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    private Subject buildSubject(Long id, String name, String color) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setColor(color);
        return subject;
    }

    private ExamDto examDto(Long id, String name, ExamType type, int daysFromNow, LocalTime time) {
        return new ExamDto(
                id,
                1L,
                "Owned Subject",
                name,
                type,
                LocalDate.now().plusDays(daysFromNow),
                time,
                120,
                "Dashboard test exam"
        );
    }

    @Test
    void authenticatedUser_canAccessDashboard() throws Exception {
        when(userRepository.findByEmail("iffat@example.com"))
                .thenReturn(Optional.of(buildUser("Iffat Jahan", "iffat@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard")
                        .with(user("iffat@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Complete your StudyFlow setup")))
                .andExpect(content().string(containsString("My Subjects")))
                .andExpect(content().string(containsString("Study Progress")));
    }

    @Test
    void dashboard_greetsAuthenticatedUserByName() throws Exception {
        when(userRepository.findByEmail("iffat@example.com"))
                .thenReturn(Optional.of(buildUser("Iffat Jahan", "iffat@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard")
                        .with(user("iffat@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iffat")))
                .andExpect(content().string(containsString("Complete your StudyFlow setup")));
    }

    @Test
    void dashboard_setupProgress_reflectsSubjects() throws Exception {
        when(userRepository.findByEmail("new@example.com"))
                .thenReturn(Optional.of(buildUser("New Student", "new@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        // No subjects yet → only account creation is completed.
        mockMvc.perform(get("/dashboard")
                        .with(user("new@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 of 5 completed")))
                .andExpect(content().string(containsString("Add your first subject")))
                // No fake statistics on an empty account.
                .andExpect(content().string(containsString("No subjects yet")));
    }

    @Test
    void dashboard_showsSubjectSnapshot() throws Exception {
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(buildUser("Test Student", "student@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of(
                buildSubject(1L, "Database", "Purple"),
                buildSubject(2L, "Mathematics", "Green")
        ));

        mockMvc.perform(get("/dashboard")
                        .with(user("student@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 of 5 completed")))
                .andExpect(content().string(containsString("Database")))
                .andExpect(content().string(containsString("Mathematics")))
                .andExpect(content().string(containsString("Purple")))
                // Snapshot must not render the empty-subjects state.
                .andExpect(content().string(not(containsString("No subjects yet"))));
    }

    @Test
    void dashboard_doesNotLeakUnauthorizedNames() throws Exception {
        when(userRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(buildUser("Another User", "student@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard")
                        .with(user("student@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Another")))
                .andExpect(content().string(not(containsString("Iffat"))));
    }

    @Test
    void dashboard_showsOnlyOwnUpcomingExams() throws Exception {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(buildUser("Alice Leigh", "alice@example.com")));
        when(userRepository.findByEmail("bob@example.com"))
                .thenReturn(Optional.of(buildUser("Bob Rune", "bob@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());
        when(examService.getExams(eq("alice@example.com"), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(examDto(1L, "Alice Exam A", ExamType.FINAL, 6, LocalTime.of(10, 0))));
        when(examService.getExams(eq("bob@example.com"), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(examDto(2L, "Bob Exam B", ExamType.MIDTERM, 3, LocalTime.of(9, 30))));
        when(examService.userHasAnyExam(any())).thenReturn(true);

        // User A sees their own exam, never User B's.
        mockMvc.perform(get("/dashboard")
                        .with(user("alice@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice Exam A")))
                .andExpect(content().string(containsString("Next Exam")))
                .andExpect(content().string(containsString("6 days")))
                .andExpect(content().string(containsString("View All Exams")))
                .andExpect(content().string(not(containsString("Bob Exam B"))));

        // User B sees their own exam, never User A's.
        mockMvc.perform(get("/dashboard")
                        .with(user("bob@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bob Exam B")))
                .andExpect(content().string(containsString("Next Exam")))
                .andExpect(content().string(containsString("3 days")))
                .andExpect(content().string(not(containsString("Alice Exam A"))));
    }

    @Test
    void dashboard_noUpcomingExams_showsEmptyStateWithAddAction() throws Exception {
        when(userRepository.findByEmail("empty@example.com"))
                .thenReturn(Optional.of(buildUser("Empty User", "empty@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());
        when(examService.getExams(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of());

        mockMvc.perform(get("/dashboard")
                        .with(user("empty@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No upcoming exams.")))
                .andExpect(content().string(containsString("Add your exams to keep important dates visible.")))
                .andExpect(content().string(containsString("/exams?add=true")))
                .andExpect(content().string(containsString("Add Exam")))
                .andExpect(content().string(not(containsString("View All Exams"))))
                .andExpect(content().string(not(containsString("Next Exam"))));
    }

    @Test
    void dashboard_upcomingExams_limitedToThreeSoonest() throws Exception {
        when(userRepository.findByEmail("limit@example.com"))
                .thenReturn(Optional.of(buildUser("Limit User", "limit@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());
        when(examService.getExams(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(
                examDto(1L, "Exam One", ExamType.FINAL, 5, LocalTime.of(10, 0)),
                examDto(2L, "Exam Two", ExamType.MIDTERM, 9, LocalTime.of(14, 0)),
                examDto(3L, "Exam Three", ExamType.QUIZ, 12, LocalTime.of(12, 30)),
                examDto(4L, "Exam Four", ExamType.VIVA, 20, LocalTime.of(9, 0)),
                examDto(5L, "Exam Five", ExamType.LAB_TEST, 21, LocalTime.of(11, 0))
        ));

        mockMvc.perform(get("/dashboard")
                        .with(user("limit@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Exam One")))
                .andExpect(content().string(containsString("Exam Two")))
                .andExpect(content().string(containsString("Exam Three")))
                .andExpect(content().string(containsString("3 upcoming")))
                .andExpect(content().string(not(containsString("Exam Four"))))
                .andExpect(content().string(not(containsString("Exam Five"))));
    }

    @Test
    void dashboard_todayExam_highlightedWithTodayUrgency() throws Exception {
        when(userRepository.findByEmail("today@example.com"))
                .thenReturn(Optional.of(buildUser("Today User", "today@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());
        when(examService.getExams(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(examDto(9L, "Mock Viva Today", ExamType.VIVA, 0, LocalTime.of(15, 0))));

        mockMvc.perform(get("/dashboard")
                        .with(user("today@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mock Viva Today")))
                .andExpect(content().string(containsString("Next Exam")))
                .andExpect(content().string(containsString("dash-urgency--today")))
                .andExpect(content().string(containsString("Today")));
    }

    @Test
    void dashboard_examSetupStep_completesWhenExamsExist() throws Exception {
        when(userRepository.findByEmail("setup@example.com"))
                .thenReturn(Optional.of(buildUser("Setup User", "setup@example.com")));
        when(subjectRepository.findAllByUserOrderByIdAsc(any()))
                .thenReturn(List.of(buildSubject(1L, "Data Structures", "Purple")));
        when(examService.getExams(eq("setup@example.com"), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(examDto(1L, "Data Structures Final", ExamType.FINAL, 14, LocalTime.of(11, 0))));
        when(examService.userHasAnyExam(eq("setup@example.com"))).thenReturn(true);

        mockMvc.perform(get("/dashboard")
                        .with(user("setup@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 of 5 completed")))
                .andExpect(content().string(containsString("Add your first exam")))
                .andExpect(content().string(containsString("Data Structures Final")));
    }

    @Test
    void anonymous_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}