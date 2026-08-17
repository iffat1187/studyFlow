package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
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
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 12 tests for the ADMIN user-management area (/admin/users).
 *
 * Covers backend authorization (ADMIN-only), anonymous redirect, the
 * search/role-filter list rendering, and the management actions
 * (enable/disable, role change, delete) including the service-layer
 * self-protection rule.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User buildUser(Long id, String name, String email, Role role, boolean enabled) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .enabled(enabled)
                .createdAt(LocalDateTime.parse("2026-04-10T09:15:00"))
                .build();
    }

    private void stubAllUsers() {
        when(userRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(
                        buildUser(2L, "Alice", "alice@example.com", Role.USER, true),
                        buildUser(3L, "Bob", "bob@example.com", Role.USER, false),
                        buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN, true)
                ));
    }

    @Test
    void admin_canViewUserList() throws Exception {
        stubAllUsers();

        mockMvc.perform(get("/admin/users")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Manage Users")))
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("bob@example.com")))
                .andExpect(content().string(containsString("Active")))
                .andExpect(content().string(containsString("Disabled")));
    }

    @Test
    void admin_canSearchUsers() throws Exception {
        when(userRepository
                .findByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc("ali", "ali"))
                .thenReturn(List.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, true)));

        mockMvc.perform(get("/admin/users")
                        .param("search", "ali")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(not(containsString("Bob"))));
    }

    @Test
    void admin_canFilterUsersByRole() throws Exception {
        when(userRepository.findByRoleOrderByCreatedAtDesc(Role.ADMIN))
                .thenReturn(List.of(buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN, true)));

        mockMvc.perform(get("/admin/users")
                        .param("role", "ADMIN")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin One")))
                .andExpect(content().string(not(containsString("Alice"))));
    }

    @Test
    void user_cannotAccessUserList() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void admin_canViewUserDetails() throws Exception {
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, true)));

        mockMvc.perform(get("/admin/users/2")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("Change Role")));
    }

    @Test
    void admin_canDisableUser() throws Exception {
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, true)));

        mockMvc.perform(post("/admin/users/2/enable")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/2"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void admin_canReenableUser() throws Exception {
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, false)));

        mockMvc.perform(post("/admin/users/2/enable")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/2"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void admin_canChangeRole() throws Exception {
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, true)));

        mockMvc.perform(post("/admin/users/2/role")
                        .param("newRole", "ADMIN")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/2"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void admin_canDeleteUser() throws Exception {
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, true)));

        mockMvc.perform(post("/admin/users/2/delete")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userRepository).delete(any(User.class));
    }

    @Test
    void admin_cannotDisableOwnAccount() throws Exception {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN, true)));

        mockMvc.perform(post("/admin/users/1/enable")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/1"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void admin_cannotDemoteOwnAccount() throws Exception {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN, true)));

        mockMvc.perform(post("/admin/users/1/role")
                        .param("newRole", "USER")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/1"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void admin_cannotDeleteOwnAccount() throws Exception {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(buildUser(1L, "Admin One", "admin@example.com", Role.ADMIN, true)));

        mockMvc.perform(post("/admin/users/1/delete")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void disabledUser_cannotLogin() throws Exception {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(buildUser(2L, "Alice", "alice@example.com", Role.USER, false)));

        mockMvc.perform(post("/login")
                        .param("email", "alice@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}