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

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * End-to-end security integration test that exercises the real
 * SecurityFilterChain, AuthenticationProvider, CustomUserDetailsService
 * and PasswordEncoder between the /login form and the protected routes.
 *
 * The UserRepository is stubbed so the test does not depend on the database,
 * but everything else uses the production Spring configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User user(String email, String rawPassword, Role role) {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return user;
    }

    @Test
    void login_validUser_redirectsToDashboard() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user("test@example.com", "password123", Role.USER)));

        mockMvc.perform(post("/login")
                        .param("email", "test@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void login_unknownEmail_redirectsToLoginError() throws Exception {
        when(userRepository.findByEmail("doesnotexist@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/login")
                        .param("email", "doesnotexist@example.com")
                        .param("password", "whatever")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_wrongPassword_redirectsToLoginError() throws Exception {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user("test@example.com", "password123", Role.USER)));

        mockMvc.perform(post("/login")
                        .param("email", "test@example.com")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unauthenticated_protectedRoute_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/subjects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void user_cannotAccessAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/")
                        .with(SecurityMockMvcRequestPostProcessors.user("test@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canPassAdminRouteAuthorization() throws Exception {
        // A 404 (no admin controller) rather than 403 proves the ADMIN
        // role passed the /admin/** authorization rule.
        mockMvc.perform(get("/admin/")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void user_canAccessAuthenticatedRoutes() throws Exception {
        mockMvc.perform(get("/subjects")
                        .with(SecurityMockMvcRequestPostProcessors.user("test@example.com").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_adminRoute_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
