package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
 * Sub-Phase E end-to-end web tests for the change-password feature against
 * the LIVE application (real beans, real DB, real PasswordEncoder).
 *
 * Verifies the full contract: valid change persists a new hash (old password
 * no longer authenticates, new one does), wrong current / mismatch / blank /
 * short / same-as-old are all rejected with no DB change, user isolation holds
 * between two users, CSRF stays enforced, and the password hash is never
 * rendered or exposed. Each test rolls back its DB writes (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangePasswordWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "cpw-" + System.nanoTime() + "@example.com";
    }

    private User saveUser(String email, String rawPassword) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.USER)
                .build());
    }

    // TEST 1 — valid change: old fails, new succeeds
    @Test
    void changePassword_valid_changePersistsNewHash_oldNoLongerAuthenticates() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "Password changed successfully!"));

        User updated = userRepository.findByEmail(email).orElseThrow();
        String storedHashAfter = updated.getPassword();

        assertThat(storedHashAfter).isNotEqualTo(storedHashBefore);
        assertThat(passwordEncoder.matches("newPass456", storedHashAfter)).isTrue();
        assertThat(passwordEncoder.matches("oldPass123", storedHashAfter)).isFalse();
    }

    // TEST 2 — wrong current password rejected, unchanged
    @Test
    void changePassword_wrongCurrent_isRejectedAndUnchanged() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "wrong-old")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Current password is incorrect.")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // TEST 3 — mismatch rejected, no DB update
    @Test
    void changePassword_mismatch_isRejectedAndUnchanged() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "Pass123")
                        .param("confirmPassword", "Pass456")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New passwords do not match.")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // TEST 4 — blank fields -> validation errors
    @Test
    void changePassword_blankFields_showsValidationErrors() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "")
                        .param("newPassword", "")
                        .param("confirmPassword", "")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "currentPassword"))
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "newPassword"))
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "confirmPassword"))
                .andExpect(content().string(containsString("Current password is required")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // TEST 5 — short new password -> validation failure
    @Test
    void changePassword_shortNewPassword_isRejected() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "abc")
                        .param("confirmPassword", "abc")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "newPassword"))
                .andExpect(content().string(containsString("at least 6 characters")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // TEST 6 — same password rejected
    @Test
    void changePassword_sameAsCurrent_isRejected() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "oldPass123")
                        .param("confirmPassword", "oldPass123")
                        .with(csrf())
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("different from your current password")));

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // TEST 43 — user isolation
    @Test
    void changePassword_affectsOnlyAuthenticatedUser() throws Exception {
        String emailA = uniqueEmail();
        String emailB = uniqueEmail();
        saveUser(emailA, "oldPass123");
        saveUser(emailB, "bobPass789");
        String bobHashBefore = userRepository.findByEmail(emailB).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf())
                        .with(user(emailA).roles("USER")))
                .andExpect(status().is3xxRedirection());

        // User B remains untouched.
        assertThat(userRepository.findByEmail(emailB).orElseThrow().getPassword()).isEqualTo(bobHashBefore);
        // User A changed.
        assertThat(passwordEncoder.matches("newPass456",
                userRepository.findByEmail(emailA).orElseThrow().getPassword())).isTrue();
    }

    // TEST 22 — CSRF enforcement
    @Test
    void changePassword_withoutCsrf_isForbidden() throws Exception {
        String email = uniqueEmail();
        saveUser(email, "oldPass123");
        String storedHashBefore = userRepository.findByEmail(email).orElseThrow().getPassword();

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(user(email).roles("USER")))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmail(email).orElseThrow().getPassword()).isEqualTo(storedHashBefore);
    }

    // anonymous redirect
    @Test
    void changePassword_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // TEST 46/47 — hash never exposed on profile page
    @Test
    void profilePage_rendersPasswordForm_butNeverExposesHash() throws Exception {
        String email = uniqueEmail();
        User user = saveUser(email, "oldPass123");

        mockMvc.perform(get("/profile")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Change Password")))
                .andExpect(content().string(containsString("Current Password")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(not(containsString(user.getPassword()))));
    }

    // TEST 44 — profile regression: picture + edit controls still present
    @Test
    void profilePage_afterPasswordSection_profileControlsStillPresent() throws Exception {
        String email = uniqueEmail();
        User user = saveUser(email, "oldPass123");
        user.setProfileImagePath("00000000-0000-0000-0000-000000000001.jpg");
        userRepository.save(user);

        mockMvc.perform(get("/profile")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Profile")))
                .andExpect(content().string(containsString("Account Information")))
                .andExpect(content().string(containsString("Change Password")));
    }
}
