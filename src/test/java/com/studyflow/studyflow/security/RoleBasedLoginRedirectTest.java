package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the role-based successful-login redirect.
 *
 * ROLE_ADMIN → /admin/dashboard
 * ROLE_USER  → /dashboard
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleBasedLoginRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User buildUser(Long id, String email, Role role) {
        return User.builder()
                .id(id)
                .name("Test User")
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .build();
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void adminLogin_redirectsToAdminDashboard() throws Exception {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(buildUser(1L, "admin@example.com", Role.ADMIN)));

        mockMvc.perform(post("/login")
                        .param("email", "admin@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    void userLogin_redirectsToDashboard() throws Exception {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(buildUser(2L, "user@example.com", Role.USER)));

        mockMvc.perform(post("/login")
                        .param("email", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void adminCanFollowRedirect_intoAdminArea() throws Exception {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(buildUser(1L, "admin@example.com", Role.ADMIN)));

        MockHttpSession session = login("admin@example.com");

        // The admin session can actually open the admin dashboard.
        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void login_logout_login_redirectsByRoleEachTime() throws Exception {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(buildUser(1L, "admin@example.com", Role.ADMIN)));
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(buildUser(2L, "user@example.com", Role.USER)));

        // 1. Log in as ADMIN → admin dashboard.
        MvcResult adminLogin = mockMvc.perform(post("/login")
                        .param("email", "admin@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andReturn();

        MockHttpSession adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // 2. Logout.
        mockMvc.perform(post("/logout").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // 3. Log in as USER → user dashboard.
        mockMvc.perform(post("/login")
                        .param("email", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }
}