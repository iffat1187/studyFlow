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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 10 tests: Spring Security logout and authentication-aware
 * navigation (navbar + landing page).
 *
 * Navbar rendering is exercised through /subjects/new because the add-form
 * page uses the shared layout that renders the profile menu, without
 * touching the database. The logout flow uses a real form-login session
 * with the UserRepository stubbed, mirroring the existing
 * LoginFlowSecurityTest setup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutFlowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    private User buildUser(String email, String rawPassword, Role role) {
        User user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return user;
    }

    private MockHttpSession loginAndGetSession(String email, String rawPassword, Role role) throws Exception {
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(buildUser(email, rawPassword, role)));

        MvcResult login = mockMvc.perform(post("/login")
                        .param("email", email)
                        .param("password", rawPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        if (session == null) {
            throw new IllegalStateException("No session created for authenticated login");
        }
        return session;
    }

    /* ---------------- TEST 1 · Anonymous navbar ---------------- */

    @Test
    void guestLandingNavigation_showsSignInAndRegister() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign In")))
                .andExpect(content().string(containsString("Get Started Free")))
                .andExpect(content().string(not(containsString("Go to Dashboard"))))
                .andExpect(content().string(not(containsString("Guest User"))));
    }

    /* ---------------- TEST 2 · USER navbar ---------------- */

    @Test
    void userNavbar_showsAuthenticatedInfoAndLogout() throws Exception {
        mockMvc.perform(get("/subjects/new")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, user@example.com")))
                .andExpect(content().string(containsString(">USER<")))
                .andExpect(content().string(not(containsString(">ADMIN<"))))
                .andExpect(content().string(containsString("Logout")))
                .andExpect(content().string(containsString("/logout")))
                .andExpect(content().string(not(containsString("Guest User"))));
    }

    /* ---------------- TEST 3 · ADMIN navbar ---------------- */

    @Test
    void adminNavbar_showsAdminRoleAndLogout() throws Exception {
        mockMvc.perform(get("/subjects/new")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, admin@example.com")))
                .andExpect(content().string(containsString(">ADMIN<")))
                .andExpect(content().string(not(containsString(">USER<"))))
                .andExpect(content().string(containsString("Logout")))
                .andExpect(content().string(containsString("/logout")))
                .andExpect(content().string(not(containsString("Guest User"))));
    }

    /* ---------------- TEST 8 · Authenticated landing navigation ---------------- */

    @Test
    void authenticatedLandingNavigation_doesNotEncourageRegistration() throws Exception {
        mockMvc.perform(get("/")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Go to Dashboard")))
                .andExpect(content().string(not(containsString("Get Started Free"))))
                .andExpect(content().string(not(containsString("Create Free Account"))));
    }

    /* ---------------- TEST 4 · Logout ---------------- */

    @Test
    void logout_redirectsToLandingPage() throws Exception {
        MockHttpSession session = loginAndGetSession("user@example.com", "password123", Role.USER);

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign In")));
    }

    /* ---------------- TESTS 5-7 · Protected routes after logout ---------------- */

    @Test
    void protectedRoutes_requireAuthenticationAgainAfterLogout() throws Exception {
        MockHttpSession session = loginAndGetSession("user@example.com", "password123", Role.USER);

        mockMvc.perform(get("/subjects/new").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        mockMvc.perform(get("/subjects").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    /* ---------------- CSRF remains enabled ---------------- */

    @Test
    void logout_withoutCsrf_isForbidden() throws Exception {
        MockHttpSession session = loginAndGetSession("user@example.com", "password123", Role.USER);

        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());
    }
}
