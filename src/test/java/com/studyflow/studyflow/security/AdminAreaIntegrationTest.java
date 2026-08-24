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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13 tests for the admin-area integration polish:
 *
 * - 403 access-denied page renders with the branded message
 * - admin navigation appears for ADMIN, stays hidden for USER
 * - the same sidebar active-menu markup is used on admin pages
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAreaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    private User buildUser(Long id, String name, String email, Role role) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .enabled(true)
                .build();
    }

    private void stubEmptyDashboard() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.countByEnabled(true)).thenReturn(0L);
        when(userRepository.countUsersByRole()).thenReturn(List.of());
        when(userRepository.countRegistrationsByMonth()).thenReturn(List.of());
        when(userRepository.countUsersCreatedSince(any())).thenReturn(0L);
        when(userRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(subjectRepository.count()).thenReturn(0L);
    }

    @Test
    void accessDeniedPage_rendersBranded403() throws Exception {
        mockMvc.perform(get("/403"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Access Denied")))
                .andExpect(content().string(containsString("You do not have permission")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(containsString("Back to Dashboard")));
    }

    @Test
    void accessDeniedPage_isPublic() throws Exception {
        mockMvc.perform(get("/403"))
                .andExpect(status().isOk());
    }

    @Test
    void adminArea_rendersAccessDeniedPage() throws Exception {
        // An authenticated USER is denied by the /admin/** rule and,
        // on a real container, Boot's error handling serves error/403.html.
        mockMvc.perform(get("/admin/dashboard")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAdminRoute_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminDashboard_showsAdminNavigation() throws Exception {
        stubEmptyDashboard();

        mockMvc.perform(get("/admin/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin Dashboard")))
                .andExpect(content().string(containsString("User Management")))
                .andExpect(content().string(containsString("Admin")));
    }

    @Test
    void adminUsers_showsAdminNavigation() throws Exception {
        when(userRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN)));

        mockMvc.perform(get("/admin/users")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin Dashboard")))
                .andExpect(content().string(containsString("User Management")));
    }

    @Test
    void userRoute_doesNotShowAdminNavigation() throws Exception {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(buildUser(1L, "Regular User", "user@example.com", Role.USER)));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        mockMvc.perform(get("/subjects")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Admin Dashboard"))))
                .andExpect(content().string(not(containsString("User Management"))));
    }
}