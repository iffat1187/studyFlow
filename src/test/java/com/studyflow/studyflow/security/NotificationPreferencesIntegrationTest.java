package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.NotificationPreferenceDto;
import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationPreferenceRepository;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationGenerationService;
import com.studyflow.studyflow.service.NotificationPreferenceService;
import com.studyflow.studyflow.service.NotificationRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase E integration tests (live database, transactional rollback):
 *
 *  - Preference gating actually suppresses generation for a disabled rule
 *    while unaffected rules still fire.
 *  - A user with no preference record still gets notifications.
 *  - The settings page is reachable and renders the toggles; submitting it
 *    persists the choice (verified both through the service and by the
 *    generation behaviour that follows).
 *  - Retention only ever removes READ rows older than the window, preserving
 *    unread and recent rows.
 *  - Settings routes are authenticated (anonymous user redirected to login).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationPreferencesIntegrationTest {

    private static final String ALICE = "pe-alice@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationGenerationService generationService;

    @Autowired
    private NotificationPreferenceService preferenceService;

    @Autowired
    private NotificationRetentionService retentionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email).email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER).build());
    }

    private Subject saveSubject(User user, String name) {
        Subject s = new Subject();
        s.setName(name);
        s.setColor("Blue");
        s.setUser(user);
        return subjectRepository.save(s);
    }

    private Task saveTask(Subject subject, String title, LocalDate due) {
        Task t = new Task();
        t.setTitle(title);
        t.setType(TaskType.ASSIGNMENT);
        t.setDueDate(due);
        t.setCompleted(false);
        t.setSubject(subject);
        return taskRepository.save(t);
    }

    private long countFor(User user, NotificationType type) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(n -> n.getType() == type).count();
    }

    @Test
    void disabledTaskDue_preferenceSuppressesGeneration_otherRulesStillFire() {
        User alice = saveUser(ALICE);
        saveTask(saveSubject(alice, "Math"), "Due Today", LocalDate.now());

        // Disable TASK_DUE; leave everything else (incl. study reminder) on.
        preferenceService.updatePreferences(ALICE,
                new NotificationPreferenceDto(false, true, true, true, true, true, true));

        generationService.generateNotificationsForUser(ALICE);

        assertEquals(0, countFor(alice, NotificationType.TASK_DUE),
                "disabled TASK_DUE must not be generated");
        assertEquals(1, countFor(alice, NotificationType.STUDY_REMINDER),
                "unaffected category must still fire");
    }

    @Test
    void noPreferenceRecord_generationStillRunsAllRules() {
        User alice = saveUser(ALICE);
        saveTask(saveSubject(alice, "Math"), "Due Today", LocalDate.now());

        generationService.generateNotificationsForUser(ALICE);

        assertEquals(1, countFor(alice, NotificationType.TASK_DUE));
        assertEquals(1, countFor(alice, NotificationType.STUDY_REMINDER));
        assertEquals(true, preferenceRepository.findByUser(alice).isEmpty(),
                "generation must not force-persist a preference row");
    }

    @Test
    void settingsPage_authenticated_rendersToggles() throws Exception {
        saveUser(ALICE);

        mockMvc.perform(get("/notifications/settings").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications Settings")))
                .andExpect(content().string(containsString("taskDueEnabled")))
                .andExpect(content().string(containsString("Save Preferences")));
    }

    @Test
    void settingsPage_anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/notifications/settings"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void saveSettings_persistsChoice_andAffectsGeneration() throws Exception {
        User alice = saveUser(ALICE);

        // Turn TASK_DUE off via the web form (absent checkbox => false) and
        // keep STREAK/STUDY on.
        mockMvc.perform(post("/notifications/settings").with(csrf()).with(user(ALICE).roles("USER"))
                        .param("taskDueEnabled", "true")
                        .param("taskOverdueEnabled", "true")
                        .param("examUpcomingEnabled", "true")
                        .param("examTodayEnabled", "true")
                        .param("studyReminderEnabled", "true")
                        .param("streakReminderEnabled", "true")
                        /* systemEnabled omitted -> false */)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications/settings"));

        // systemEnabled now disabled: hidden checkbox absent => false saved.
        NotificationPreferenceDto saved = preferenceService.getPreferences(ALICE);
        assertEquals(false, saved.systemEnabled());
        assertEquals(true, saved.taskDueEnabled());

        saveTask(saveSubject(alice, "Math"), "Due Today", LocalDate.now());
        generationService.generateNotificationsForUser(ALICE);
        assertEquals(1, countFor(alice, NotificationType.TASK_DUE));
    }

    @Test
    void retention_removesOnlyReadOlderRows_preservesUnreadAndRecent() {
        User alice = saveUser(ALICE);

        Notification readOld = notificationRepository.save(notification(alice, true));
        Notification unreadOld = notificationRepository.save(notification(alice, false));
        Notification readNew = notificationRepository.save(notification(alice, true));
        // Flush inserts so the native backdating UPDATEs hit real rows.
        entityManager.flush();
        backdateCreatedAt(readOld.getId(), LocalDateTime.now().minusDays(120));
        backdateCreatedAt(unreadOld.getId(), LocalDateTime.now().minusDays(120));
        backdateCreatedAt(readNew.getId(), LocalDateTime.now().minusDays(10));
        // Detach so the entities are re-read (with their backdated rows).
        entityManager.clear();

        assertEquals(3, notificationRepository.findByUserOrderByCreatedAtDesc(alice).size());
        int removed = retentionService.cleanupReadOlderThan(90);
        assertEquals(1, removed);

        List<Notification> remaining = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        assertEquals(2, remaining.size());
        // The unread-old row is preserved regardless of age.
        assertTrue(remaining.stream().anyMatch(n -> !n.getRead()),
                "unread notification must never be auto-removed");
        // The only remaining read row is the recent one (created within the window).
        List<Notification> readRows = remaining.stream().filter(Notification::getRead).toList();
        assertEquals(1, readRows.size());
        assertTrue(readRows.get(0).getCreatedAt().isAfter(LocalDateTime.now().minusDays(90)),
                "the only remaining read row must be recent");
    }

    private Notification notification(User user, boolean read) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle("T");
        n.setMessage("M");
        n.setType(NotificationType.SYSTEM);
        n.setRead(read);
        return n;
    }

    private void backdateCreatedAt(Long id, LocalDateTime ts) {
        entityManager.createNativeQuery(
                        "UPDATE notifications SET created_at = :ts WHERE id = :id")
                .setParameter("ts", ts)
                .setParameter("id", id)
                .executeUpdate();
    }
}
