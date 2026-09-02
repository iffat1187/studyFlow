package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.entity.NotificationPreference;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationPreferenceRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Sub-Phase F integration tests (live database, transactional rollback) for
 * the Study Preferences (Settings) routes:
 *
 *  - Anonymous access is redirected to login.
 *  - The page renders documented defaults and lazily creates the settings
 *    row on first open.
 *  - A valid save persists the approved fields and flashes the success
 *    message; a follow-up GET renders the saved values.
 *  - Invalid values (zero/negative goal, over-long goal) are rejected, the
 *    form re-renders with the error, and nothing is persisted.
 *  - Unparseable session types are rejected as binding errors.
 *  - The token is required (CSRF enforced).
 *  - Settings are per-user: one user's save never leaks into another user's
 *    Settings page.
 *  - Saving settings never touches notification preferences, and never
 *    touches the user's identity (email, password hash, role, picture).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsWebIntegrationTest {

    private static final String ALICE = "settings-it-alice@example.com";
    private static final String BOB = "settings-it-bob@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email).email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER).build());
    }

    // TEST 1 — anonymous access is secured.
    @Test
    void anonymousAccess_settingsPage_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // TEST 1b — anonymous POST is secured too (token present, so the CSRF
    // filter passes and the authorization entry point issues the login
    // redirect).
    @Test
    void anonymousPost_settings_redirectedToLogin() throws Exception {
        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // TEST 2 — first open renders documented defaults and lazily creates a row.
    @Test
    void settingsPage_firstOpen_rendersDefaultsAndCreatesRow() throws Exception {
        User alice = saveUser(ALICE);

        mockMvc.perform(get("/settings").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("userSettingsDto", equalTo(UserSettingsDto.defaults())))
                .andExpect(content().string(containsString("Default Focus Duration")))
                .andExpect(content().string(containsString("Weekly Study Goal")));

        assertTrue(userSettingsRepository.findByUser(alice).isPresent(),
                "opening Settings must lazily create the default row");
        assertThat(userSettingsRepository.findByUser(alice).get().getDefaultFocusDurationMinutes()).isEqualTo(25);
        assertThat(userSettingsRepository.findByUser(alice).get().getWeeklyStudyGoalMinutes()).isEqualTo(300);
    }

    // TEST 3 — a saved row is rendered back.
    @Test
    void settingsPage_existingRow_rendersSavedValues() throws Exception {
        User alice = saveUser(ALICE);
        com.studyflow.studyflow.entity.UserSettings s = new com.studyflow.studyflow.entity.UserSettings();
        s.setUser(alice);
        s.setDefaultFocusDurationMinutes(30);
        s.setDefaultSessionType(com.studyflow.studyflow.entity.StudySessionType.REVISION);
        s.setWeeklyStudyGoalMinutes(600);
        userSettingsRepository.save(s);

        mockMvc.perform(get("/settings").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("userSettingsDto",
                        equalTo(new UserSettingsDto(30,
                                com.studyflow.studyflow.entity.StudySessionType.REVISION, 600))));
    }

    // TEST 4 — valid save: PRG redirect, success flash, values persisted.
    @Test
    void saveSettings_valid_persistsAndFlashes() throws Exception {
        User alice = saveUser(ALICE);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("successMessage", "Settings saved successfully."));

        var row = userSettingsRepository.findByUser(alice).orElseThrow();
        assertThat(row.getDefaultFocusDurationMinutes()).isEqualTo(30);
        assertThat(row.getDefaultSessionType()).isEqualTo(com.studyflow.studyflow.entity.StudySessionType.REVISION);
        assertThat(row.getWeeklyStudyGoalMinutes()).isEqualTo(600);

        mockMvc.perform(get("/settings").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("userSettingsDto",
                        equalTo(new UserSettingsDto(30,
                                com.studyflow.studyflow.entity.StudySessionType.REVISION, 600))));
    }

    // TEST 5 — zero weekly goal is rejected; nothing is persisted.
    @Test
    void saveSettings_zeroGoal_isRejectedAndNotPersisted() throws Exception {
        User alice = saveUser(ALICE);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "0")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly study goal must be at least 1 minute.")));

        assertTrue(userSettingsRepository.findByUser(alice).isEmpty(),
                "rejected save must not create a settings row");
    }

    // TEST 6 — negative goal rejected; existing row unchanged.
    @Test
    void saveSettings_negativeGoal_isRejectedAndKeepsSavedValues() throws Exception {
        User alice = saveUser(ALICE);
        com.studyflow.studyflow.entity.UserSettings row = new com.studyflow.studyflow.entity.UserSettings();
        row.setUser(alice);
        row.setDefaultFocusDurationMinutes(30);
        row.setDefaultSessionType(com.studyflow.studyflow.entity.StudySessionType.REVISION);
        row.setWeeklyStudyGoalMinutes(600);
        userSettingsRepository.save(row);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "-45")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly study goal must be at least 1 minute.")));

        assertThat(userSettingsRepository.findByUser(alice).get().getWeeklyStudyGoalMinutes()).isEqualTo(600);
    }

    // TEST 7 — over-long goal rejected.
    @Test
    void saveSettings_goalOverMaximum_isRejected() throws Exception {
        saveUser(ALICE);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "25")
                        .param("defaultSessionType", "FOCUS")
                        .param("weeklyStudyGoalMinutes", "10001")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly study goal cannot exceed 10000 minutes.")));
    }

    // TEST 8 — an unparseable session type is a binding error, never persisted.
    @Test
    void saveSettings_bogusSessionType_reRendersWithError() throws Exception {
        User alice = saveUser(ALICE);
        com.studyflow.studyflow.entity.UserSettings row = new com.studyflow.studyflow.entity.UserSettings();
        row.setUser(alice);
        row.setDefaultFocusDurationMinutes(30);
        row.setDefaultSessionType(com.studyflow.studyflow.entity.StudySessionType.REVISION);
        row.setWeeklyStudyGoalMinutes(600);
        userSettingsRepository.save(row);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "NOT-A-TYPE")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userSettingsDto", "defaultSessionType"));

        var reloaded = userSettingsRepository.findByUser(alice).orElseThrow();
        assertThat(reloaded.getDefaultSessionType()).isEqualTo(com.studyflow.studyflow.entity.StudySessionType.REVISION);
        assertThat(reloaded.getWeeklyStudyGoalMinutes()).isEqualTo(600);
    }

    // TEST 9 — blank fields are validation errors, nothing persisted.
    @Test
    void saveSettings_blankFields_reRendersWithErrors() throws Exception {
        saveUser(ALICE);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "")
                        .param("defaultSessionType", "FOCUS")
                        .param("weeklyStudyGoalMinutes", "")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Default focus duration is required.")))
                .andExpect(content().string(containsString("Weekly study goal is required.")));

        assertTrue(userSettingsRepository.findByUser(userRepository.findByEmail(ALICE).orElseThrow()).isEmpty());
    }

    // TEST 10 — CSRF is enforced.
    @Test
    void saveSettings_withoutCsrf_isForbidden() throws Exception {
        saveUser(ALICE);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isForbidden());
    }

    // TEST 11 — user isolation: Alice's save never leaks into Bob's page.
    @Test
    void settings_isolation_twoUsersIndependent() throws Exception {
        saveUser(ALICE);
        User bob = saveUser(BOB);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/settings").with(user(BOB).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("userSettingsDto", equalTo(UserSettingsDto.defaults())));

        assertThat(userSettingsRepository.findByUser(bob).get().getDefaultFocusDurationMinutes()).isEqualTo(25);
        assertThat(userSettingsRepository.findByUser(bob).get().getWeeklyStudyGoalMinutes()).isEqualTo(300);
    }

    // TEST 12 — saving settings never touches notification preferences.
    @Test
    void saveSettings_doesNotTouchNotificationPreferences() throws Exception {
        User alice = saveUser(ALICE);
        NotificationPreference prefs = new NotificationPreference();
        prefs.setUser(alice);
        prefs.setTaskDueEnabled(false);
        preferenceRepository.save(prefs);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().is3xxRedirection());

        NotificationPreference after = preferenceRepository.findByUser(alice).orElseThrow();
        assertFalse(after.isTaskDueEnabled(), "notification preference must survive a settings save");
        assertTrue(after.isExamTodayEnabled(), "other categories must stay untouched");
    }

    // TEST 13 — saving settings never touches profile identity or picture.
    @Test
    void saveSettings_doesNotTouchUserIdentityOrPicture() throws Exception {
        User alice = saveUser(ALICE);
        String hash = alice.getPassword();
        String email = alice.getEmail();
        alice.setProfileImagePath("existing-picture.jpg");
        User stored = userRepository.save(alice);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().is3xxRedirection());

        User after = userRepository.findByEmail(ALICE).orElseThrow();
        assertThat(after.getEmail()).isEqualTo(email);
        assertThat(after.getPassword()).isEqualTo(hash);
        assertThat(after.getRole()).isEqualTo(Role.USER);
        assertThat(after.getProfileImagePath()).isEqualTo("existing-picture.jpg");
        assertTrue(stored.getProfileImagePath().equals("existing-picture.jpg"));
    }
}