package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase C web-layer integration tests for the notification center.
 *
 * Authenticates via MockMvc {@code user(...)} post-processors against
 * in-DB user rows so the controllers resolve the principal to a real owner.
 * Verifies the secured routes, the server-rendered page and JSON endpoints,
 * the unread-count transitions after mark-as-read / mark-all, the dropdown
 * limit, and IDOR protection (one user can never read or modify another
 * user's notifications). Records are seeded in a transaction and rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationCenterWebIntegrationTest {

    private static final String ALICE = "c-alice@example.com";
    private static final String BOB = "c-bob@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private User ensureUser(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> saveUser(email));
    }

    // ------------------------------------------------------------------
    // ROUTE SECURITY
    // ------------------------------------------------------------------

    @Test
    void notificationsRoute_requiresAuthentication() throws Exception {
        saveUser(ALICE);
        mockMvc.perform(get("/notifications"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void notificationsRoute_supportsUser() throws Exception {
        saveUser(ALICE);
        mockMvc.perform(get("/notifications")
                        .with(user(ALICE).roles("USER")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // NOTIFICATION PAGE (server-rendered, newest first)
    // ------------------------------------------------------------------

    @Test
    void page_rendersOwnNotificationsNewestFirst() throws Exception {
        User alice = ensureUser(ALICE);
        notificationService.createNotification(ALICE, "First Title", "first msg", NotificationType.TASK_DUE);
        notificationService.createNotification(ALICE, "Second Title", "second msg", NotificationType.EXAM_TODAY);
        notificationRepository.flush();

        mockMvc.perform(get("/notifications").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Second Title")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("First Title")));
    }

    @Test
    void page_withNoNotifications_showsEmptyState() throws Exception {
        saveUser(ALICE);
        mockMvc.perform(get("/notifications").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("No notifications yet")));
    }

    // ------------------------------------------------------------------
    // UNREAD COUNT
    // ------------------------------------------------------------------

    @Test
    void unreadCount_tracksCurrentUserOnly() throws Exception {
        ensureUser(ALICE);
        ensureUser(BOB);
        notificationService.createNotification(ALICE, "A1", "m", NotificationType.SYSTEM);
        notificationService.createNotification(ALICE, "A2", "m", NotificationType.SYSTEM);
        notificationService.createNotification(ALICE, "A3", "m", NotificationType.SYSTEM);
        notificationService.createNotification(BOB, "B1", "m", NotificationType.SYSTEM);
        notificationRepository.flush();

        mockMvc.perform(get("/notifications/unread-count").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));

        mockMvc.perform(get("/notifications/unread-count").with(user(BOB).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    // ------------------------------------------------------------------
    // MARK AS READ + COUNT TRANSITION (spec step)
    // ------------------------------------------------------------------

    @Test
    void markRead_transitionsUnreadToRead_forCurrentUserOnly() throws Exception {
        User alice = ensureUser(ALICE);
        notificationService.createNotification(ALICE, "A1", "m", NotificationType.TASK_DUE);
        notificationService.createNotification(ALICE, "A2", "m", NotificationType.SYSTEM);
        notificationRepository.flush();

        // Before: 2 unread.
        mockMvc.perform(get("/notifications/unread-count").with(user(ALICE).roles("USER")))
                .andExpect(jsonPath("$.unreadCount").value(2));

        Long firstId = notificationRepository.findByUserOrderByCreatedAtDesc(alice).get(0).getId();

        mockMvc.perform(post("/notifications/read/" + firstId)
                        .with(user(ALICE).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // After marking one read: 1 remains unread.
        mockMvc.perform(get("/notifications/unread-count").with(user(ALICE).roles("USER")))
                .andExpect(jsonPath("$.unreadCount").value(1));

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findByIdAndUser(firstId, alice)
                        .map(n -> Boolean.TRUE.equals(n.getRead()))
                        .orElse(false));
    }

    // ------------------------------------------------------------------
    // MARK ALL AS READ
    // ------------------------------------------------------------------

    @Test
    void markAllRead_clearsUnreadCount() throws Exception {
        ensureUser(ALICE);
        notificationService.createNotification(ALICE, "A1", "m", NotificationType.SYSTEM);
        notificationService.createNotification(ALICE, "A2", "m", NotificationType.SYSTEM);
        notificationService.createNotification(ALICE, "A3", "m", NotificationType.SYSTEM);
        notificationRepository.flush();

        mockMvc.perform(post("/notifications/read-all")
                        .with(user(ALICE).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));

        mockMvc.perform(get("/notifications/unread-count").with(user(ALICE).roles("USER")))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ------------------------------------------------------------------
    // DROPDOWN RECENT LIMIT
    // ------------------------------------------------------------------

    @Test
    void recent_dropdownLimitedToFive() throws Exception {
        ensureUser(ALICE);
        for (int i = 1; i <= 7; i++) {
            notificationService.createNotification(ALICE, "N" + i, "m", NotificationType.SYSTEM);
        }
        notificationRepository.flush();

        mockMvc.perform(get("/notifications/recent").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    // ------------------------------------------------------------------
    // MODULE NAVIGATION + ICON MAPPING ARE NOT EXPOSED AS IDs
    // ------------------------------------------------------------------

    @Test
    void recent_exposesTypeHostAndSafeHref_neverSourceId() throws Exception {
        ensureUser(ALICE);
        notificationService.createNotification(ALICE, "Due", "m", NotificationType.TASK_DUE);
        notificationRepository.flush();

        mockMvc.perform(get("/notifications/recent").with(user(ALICE).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TASK_DUE"))
                .andExpect(jsonPath("$[0].href").value("/tasks"))
                .andExpect(jsonPath("$[0].icon").exists());
    }

    // ------------------------------------------------------------------
    // IDOR: USER B MUST NOT SEE OR MODIFY USER A'S NOTIFICATIONS
    // ------------------------------------------------------------------

    @Test
    void idor_bobCannotSeeOrModifyAlicesNotifications() throws Exception {
        User alice = ensureUser(ALICE);
        ensureUser(BOB);
        notificationService.createNotification(ALICE, "Alice Secret", "m", NotificationType.SYSTEM);
        notificationRepository.flush();

        Long aliceId = notificationRepository.findByUserOrderByCreatedAtDesc(alice).get(0).getId();

        // Bob's page must not contain Alice's notification title.
        mockMvc.perform(get("/notifications").with(user(BOB).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("Alice Secret"))));

        // Bob's unread-count reflects only Bob (0), never Alice's 1.
        mockMvc.perform(get("/notifications/unread-count").with(user(BOB).roles("USER")))
                .andExpect(jsonPath("$.unreadCount").value(0));

        // Bob cannot mark Alice's notification read -> rejected, unchanged.
        mockMvc.perform(post("/notifications/read/" + aliceId)
                        .with(user(BOB).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/notifications/unread-count").with(user(ALICE).roles("USER")))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    // ------------------------------------------------------------------
    // CSRF ON WRITE ENDPOINTS
    // ------------------------------------------------------------------

    @Test
    void markRead_rejectsRequestWithoutCsrfToken() throws Exception {
        ensureUser(ALICE);
        mockMvc.perform(post("/notifications/read/1").with(user(ALICE).roles("USER")))
                .andExpect(status().isForbidden());
    }
}
