package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
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

import java.time.LocalDateTime;
import java.util.List;

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
 * Phase 11 tests for the ADMIN dashboard route /admin/dashboard.
 *
 * Covers backend authorization (ADMIN-only), anonymous redirect and that
 * the rendered dashboard uses the real aggregated statistics supplied by
 * the admin service layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    private User buildUser(Long id, String name, String email, Role role, LocalDateTime createdAt) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .createdAt(createdAt)
                .build();
    }

    private void stubRealisticDashboardData() {
        when(userRepository.count()).thenReturn(5L);
        when(subjectRepository.count()).thenReturn(12L);
        when(userRepository.countUsersByRole())
                .thenReturn(List.of(
                        new Object[]{Role.USER, 4L},
                        new Object[]{Role.ADMIN, 1L}
                ));
        when(userRepository.countUsersCreatedSince(any(LocalDateTime.class))).thenReturn(2L);
        when(userRepository.countRegistrationsByMonth())
                .thenReturn(List.of(
                        new Object[]{"2026-03", 2L},
                        new Object[]{"2026-04", 3L}
                ));
        when(userRepository.findTop5ByOrderByCreatedAtDesc())
                .thenReturn(List.of(
                        buildUser(2L, "Alice", "alice@example.com", Role.USER, LocalDateTime.parse("2026-04-10T09:15:00")),
                        buildUser(1L, "Bob", "bob@example.com", Role.ADMIN, LocalDateTime.parse("2026-03-05T14:00:00"))
                ));
    }

    @Test
    void admin_canAccessDashboard() throws Exception {
        stubRealisticDashboardData();

        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin Dashboard")))
                .andExpect(content().string(containsString("Total Users")))
                .andExpect(content().string(containsString("Mar 2026")));
    }

    @Test
    void dashboard_displaysRealStatistics() throws Exception {
        stubRealisticDashboardData();

        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">5</strong>")))
                .andExpect(content().string(containsString(">12</strong>")))
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("alice@example.com")))
                .andExpect(content().string(containsString("ADMIN")))
                .andExpect(content().string(containsString("USER")))
                .andExpect(content().string(containsString("roleCounts: [4,1]")))
                .andExpect(content().string(containsString("trendCounts: [2,3]")));
    }

    @Test
    void user_cannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}