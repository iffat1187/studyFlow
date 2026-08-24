package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
    void anonymous_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}