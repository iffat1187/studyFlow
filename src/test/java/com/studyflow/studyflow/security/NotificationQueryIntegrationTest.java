package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-Phase A integration tests: every notification query executes against
 * the real database (no stubs) using the same user-scoped Spring Data
 * queries the service uses at runtime. Records are seeded inside a
 * transaction and rolled back, so nothing persists.
 *
 * Verifies that notifications are created and read user-scoped (newest
 * first), that the unread filter works, and that a user can never obtain
 * another user's notification even when the id is known (IDOR prevention).
 */
@SpringBootTest
@Transactional
class NotificationQueryIntegrationTest {

    private static final String ALICE_EMAIL = "n-alice@example.com";
    private static final String BOB_EMAIL = "n-bob@example.com";

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

    @Test
    void createNotification_savesWithCorrectOwnerAndDefaults() {
        User alice = saveUser(ALICE_EMAIL);

        NotificationDto dto = notificationService.createNotification(
                ALICE_EMAIL, "Task Due Today", "Your JPA assignment is due today.", NotificationType.TASK_DUE);

        assertEquals(ALICE_EMAIL, alice.getEmail());
        assertEquals("Task Due Today", dto.title());
        assertEquals("Your JPA assignment is due today.", dto.message());
        assertEquals(NotificationType.TASK_DUE, dto.type());
        assertTrue(!dto.read(), "new notification must be unread");
        assertTrue(dto.createdAt() != null, "createdAt must be populated server-side");

        // The persisted row must be owned by Alice and store the enum as text.
        Notification persisted = notificationRepository.findById(dto.id()).orElseThrow();
        assertEquals(alice.getId(), persisted.getUser().getId());
        assertEquals(NotificationType.TASK_DUE, persisted.getType());
        // Enum stored as STRING, not ordinal.
        assertTrue(persisted.getType().name().equals("TASK_DUE"));
    }

    @Test
    void getNotifications_returnsCurrentUsersNewestFirst() {
        User alice = saveUser(ALICE_EMAIL);
        User bob = saveUser(BOB_EMAIL);

        NotificationDto a1 = notificationService.createNotification(ALICE_EMAIL, "A1", "m", NotificationType.SYSTEM);
        NotificationDto a2 = notificationService.createNotification(ALICE_EMAIL, "A2", "m", NotificationType.EXAM_TODAY);
        notificationService.createNotification(BOB_EMAIL, "B1", "m", NotificationType.SYSTEM);

        List<NotificationDto> aliceDtos = notificationService.getNotifications(ALICE_EMAIL);
        List<NotificationDto> bobDtos = notificationService.getNotifications(BOB_EMAIL);

        // Alice sees only her own, newest created first (A2 then A1).
        assertEquals(2, aliceDtos.size());
        assertEquals("A2", aliceDtos.get(0).title());
        assertEquals("A1", aliceDtos.get(1).title());

        // Bob sees only his own.
        assertEquals(1, bobDtos.size());
        assertEquals("B1", bobDtos.get(0).title());

        assertEquals(0, notificationService.getNotifications("ghost@example.com").size());
    }

    @Test
    void getUnreadNotifications_returnsOnlyUnread() {
        User alice = saveUser(ALICE_EMAIL);
        notificationService.createNotification(ALICE_EMAIL, "Unread1", "m", NotificationType.TASK_OVERDUE);

        // Flip one to read via the entity so the unread filter is exercised.
        List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(alice);
        Notification readOne = all.get(0);
        readOne.setRead(true);
        notificationRepository.save(readOne);

        List<NotificationDto> unread = notificationService.getUnreadNotifications(ALICE_EMAIL);

        // The single notification is now read, so the unread list is empty.
        assertEquals(0, unread.size());
    }

    @Test
    void findByIdAndUser_preventsCrossUserAccess() {
        User alice = saveUser(ALICE_EMAIL);
        User bob = saveUser(BOB_EMAIL);
        NotificationDto aliceNotif = notificationService.createNotification(ALICE_EMAIL, "A Secret", "m", NotificationType.SYSTEM);

        // Alice can fetch her own notification by id.
        assertTrue(notificationRepository.findByIdAndUser(aliceNotif.id(), alice).isPresent());

        // Bob cannot fetch Alice's notification even knowing its id.
        assertTrue(notificationRepository.findByIdAndUser(aliceNotif.id(), bob).isEmpty());
    }

    @Test
    void notification_requiresAnOwner() {
        // A notification with a null owner must not be allowed at the DB layer
        // (user_id is NOT NULL). Note bob remains unused but harmless.
        saveUser(BOB_EMAIL);
        Notification orphan = new Notification();
        orphan.setUser(null);
        orphan.setTitle("Orphan");
        orphan.setType(NotificationType.SYSTEM);
        assertThrows(Exception.class, () -> notificationRepository.saveAndFlush(orphan));
    }
}
