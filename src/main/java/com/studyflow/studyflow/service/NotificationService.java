package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.entity.NotificationType;

import java.util.List;

/**
 * Service responsible for the authenticated user's notifications.
 *
 * Identity is never supplied by the browser: every method takes the
 * authenticated user's email (resolved from the Spring Security context by
 * the calling controller) and resolves the owning {@code User} internally.
 * All retrieval is user-scoped, so one user can never observe another
 * user's notifications (IDOR prevention).
 *
 * This sub-phase provides the persistent foundation only: creating
 * notifications, reading the current user's notifications and reading their
 * unread notifications. Automated generation, scheduling, email and push are
 * intentionally out of scope.
 */
public interface NotificationService {

    /**
     * Create and persist a notification for the authenticated user.
     *
     * The notification is immediately unread with its creation time assigned
     * server-side. An unknown account yields no notification (returns null).
     *
     * @param userEmail the authenticated user's email (from the security context)
     * @param title     short headline text
     * @param message   longer descriptive text
     * @param type      notification category
     * @return the created DTO, or null when the user cannot be resolved
     */
    NotificationDto createNotification(String userEmail, String title, String message, NotificationType type);

    /**
     * Create and persist a notification with a deterministic deduplication
     * key. Used by the generation service so that evaluating a rule
     * repeatedly never produces duplicate records.
     *
     * @param userEmail the authenticated user's email
     * @param title     short headline text
     * @param message   longer descriptive text
     * @param type      notification category
     * @param sourceKey deterministic key identifying the underlying event
     * @return the created DTO, or null when the user cannot be resolved
     */
    NotificationDto createNotification(String userEmail, String title, String message,
                                       NotificationType type, String sourceKey);

    /**
     * The authenticated user's notifications, newest first.
     *
     * @param userEmail the authenticated user's email
     * @return the user's notifications, newest first
     */
    List<NotificationDto> getNotifications(String userEmail);

    /**
     * The authenticated user's unread notifications only, newest first.
     *
     * @param userEmail the authenticated user's email
     * @return the user's unread notifications, newest first
     */
    List<NotificationDto> getUnreadNotifications(String userEmail);

    /**
     * The authenticated user's unread notification count used by the navbar
     * bell badge.
     *
     * @param userEmail the authenticated user's email
     * @return the number of unread notifications (0 for unknown accounts)
     */
    long getUnreadCount(String userEmail);

    /**
     * The authenticated user's newest notifications, limited to {@code limit}
     * entries. Used by the navbar dropdown; the full history lives on the
     * notification page.
     *
     * @param userEmail the authenticated user's email
     * @param limit     maximum number of entries to return
     * @return the user's newest notifications up to the requested limit
     */
    List<NotificationDto> getRecentNotifications(String userEmail, int limit);

    /**
     * Mark a single notification as read, but only when it belongs to the
     * authenticated user. A notification id belonging to another user is
     * ignored (IDOR prevention).
     *
     * @param userEmail the authenticated user's email
     * @param id        the notification primary key
     * @return true when a notification was found and marked read, false
     *         otherwise (unknown user, unknown id, or not owned)
     */
    boolean markAsRead(String userEmail, Long id);

    /**
     * Mark all of the authenticated user's unread notifications as read.
     *
     * @param userEmail the authenticated user's email
     * @return the number of notifications flipped to read (0 for unknown
     *         accounts or when there was nothing unread)
     */
    int markAllAsRead(String userEmail);
}
