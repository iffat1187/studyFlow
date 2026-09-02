package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.StudySessionDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.entity.UserSettings;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase F feature integration tests (live database, transactional) tying
 * the user's Settings into the product:
 *
 *  - The weekly study goal renders on the dashboard as real progress (week
 *    minutes over the user's own goal), and never leaks into the empty state
 *    of a user without study data.
 *  - The Focus Timer opens with the user's default focus duration, and the
 *    Study Session form + Focus Timer pre-select the user's default session
 *    type.
 *  - Users WITHOUT settings rows keep the documented defaults (25 / FOCUS),
 *    so existing behaviour is preserved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsFeatureIntegrationTest {

    private static final String ALICE = "feature-alice@example.com";
    private static final String EMPTY = "feature-empty@example.com";
    private static final String DEFAULT_USER = "feature-default@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email).email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER).build());
    }

    private void saveSettings(User user, int duration, StudySessionType type, int goal) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setDefaultFocusDurationMinutes(duration);
        settings.setDefaultSessionType(type);
        settings.setWeeklyStudyGoalMinutes(goal);
        userSettingsRepository.save(settings);
    }

    private void saveSession(User user, int durationMinutes, int daysAgo) {
        Subject subject = new Subject();
        subject.setName("Math");
        subject.setColor("Blue");
        subject.setUser(user);
        subjectRepository.save(subject);

        StudySession session = new StudySession();
        session.setSubject(subject);
        session.setSessionType(StudySessionType.FOCUS);
        session.setSessionDate(LocalDate.now().minusDays(daysAgo));
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(9, 0).plusMinutes(durationMinutes));
        session.setDurationMinutes(durationMinutes);
        studySessionRepository.save(session);
    }

    // TEST 1 — dashboard renders the weekly goal against real week minutes.
    @Test
    void dashboard_rendersWeeklyGoalProgress() throws Exception {
        User alice = saveUser(ALICE);
        saveSettings(alice, 30, StudySessionType.REVISION, 300);
        // Week: two sessions today totalling 155m of a 300m goal => 52%.
        saveSession(alice, 75, 0);
        saveSession(alice, 80, 0);

        mockMvc.perform(get("/dashboard").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly Goal")))
                .andExpect(content().string(containsString("2h 35m of 5h")))
                .andExpect(content().string(containsString("52% of weekly goal")));
    }

    // TEST 2 — no goal leaks into the empty state of a user without studies.
    @Test
    void dashboard_userWithoutStudyData_doesNotShowGoal() throws Exception {
        saveUser(EMPTY);

        mockMvc.perform(get("/dashboard").with(user(EMPTY).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Start your first study session")))
                .andExpect(content().string(not(containsString("Weekly Goal"))))
                .andExpect(content().string(not(containsString("of weekly goal"))));
    }

    // TEST 3 — dashboard goal is per-user: Alice's goal never shows for Bob.
    @Test
    void dashboard_goalIsolatedPerUser() throws Exception {
        User alice = saveUser(ALICE);
        saveSettings(alice, 30, StudySessionType.REVISION, 300);
        saveSession(alice, 75, 0);
        saveUser(EMPTY);

        mockMvc.perform(get("/dashboard").with(user(EMPTY).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Weekly Goal"))));
    }

    // TEST 4 — Focus Timer + new-session form adopt the user's defaults.
    @Test
    void studySessionsPage_usesUsersDefaults() throws Exception {
        User carol = saveUser("feature-carol@example.com");
        saveSettings(carol, 45, StudySessionType.EXAM_PREPARATION, 600);

        mockMvc.perform(get("/study-sessions/new")
                        .with(user("feature-carol@example.com").roles("USER")))
                .andExpect(status().isOk())
                // The create form's default type comes from Settings.
                .andExpect(model().attribute("studySessionDto",
                        equalTo(new StudySessionDto(
                                null, null, null, null, null,
                                LocalDate.now(), null, null, 60,
                                StudySessionType.EXAM_PREPARATION, ""))))
                // The Focus Timer hidden input carries the saved default.
                .andExpect(content().string(containsString("data-focus-timer-duration")))
                .andExpect(content().string(containsString("value=\"45\"")))
                // Timer display shows the saved default.
                .andExpect(content().string(containsString("45 min")))
                // Default type pre-selected in the Focus Timer type dropdown.
                .andExpect(content().string(containsString(
                        "value=\"EXAM_PREPARATION\" selected=\"selected\"")));
    }

    // TEST 5 — users without settings rows keep the documented defaults.
    @Test
    void studySessionsPage_noSettingsRow_usesDocumentedDefaults() throws Exception {
        saveUser(DEFAULT_USER);

        mockMvc.perform(get("/study-sessions/new")
                        .with(user(DEFAULT_USER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("studySessionDto",
                        equalTo(new StudySessionDto(
                                null, null, null, null, null,
                                LocalDate.now(), null, null, 60,
                                StudySessionType.FOCUS, ""))))
                .andExpect(content().string(containsString("value=\"25\"")))
                .andExpect(content().string(containsString(
                        "value=\"FOCUS\" selected=\"selected\"")));
    }
}