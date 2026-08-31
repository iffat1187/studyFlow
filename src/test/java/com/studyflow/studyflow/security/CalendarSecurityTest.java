package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.SubjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Phase calendar B+C tests for the authenticated USER calendar routes.
 *
 * Covers anonymous protection (redirect to login) and that an authenticated
 * user can reach the calendar page and see an honest empty state when they
 * have no events in the displayed range.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private ExamRepository examRepository;

    @MockitoBean
    private StudySessionRepository studySessionRepository;

    @MockitoBean
    private SubjectService subjectService;

    private User buildUser(String name, String email) {
        return User.builder()
                .id(1L)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    @Test
    void anonymousUser_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/calendar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonymousUser_redirectedToLogin_forEventsEndpoint() throws Exception {
        mockMvc.perform(get("/calendar/events")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedUser_canAccessCalendarPage() throws Exception {
        when(userRepository.findByEmail("iffat@example.com"))
                .thenReturn(Optional.of(buildUser("Iffat Jahan", "iffat@example.com")));
        when(taskRepository.findBySubject_UserAndDueDateBetween(any(), any(), any())).thenReturn(List.of());
        when(examRepository.findBySubject_UserAndExamDateBetween(any(), any(), any())).thenReturn(List.of());
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(subjectService.getAllSubjects(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/calendar")
                        .with(user("iffat@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Calendar")))
                .andExpect(content().string(containsString("All Subjects")));
    }

    @Test
    void eventsEndpoint_requiresDates() throws Exception {
        when(userRepository.findByEmail("iffat@example.com"))
                .thenReturn(Optional.of(buildUser("Iffat Jahan", "iffat@example.com")));
        when(taskRepository.findBySubject_UserAndDueDateBetween(any(), any(), any())).thenReturn(List.of());
        when(examRepository.findBySubject_UserAndExamDateBetween(any(), any(), any())).thenReturn(List.of());
        when(studySessionRepository.findBySubject_UserAndSessionDateBetween(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/calendar/events")
                        .with(user("iffat@example.com").roles("USER"))
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test
    void eventsEndpoint_missingDates_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/calendar/events")
                        .with(user("iffat@example.com").roles("USER")))
                .andExpect(status().isBadRequest());
    }
}
