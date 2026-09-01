package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Notification entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used for user-facing retrieval is scoped to the owning
 * {@link User} so that one user can never observe another user's
 * notifications (IDOR prevention). {@link #findAll()} is deliberately not
 * used for normal user-facing retrieval.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * All notifications belonging to the given user, newest first.
     *
     * @param user the owning user
     * @return the user's notifications, newest first
     */
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    /**
     * The given user's unread notifications only, newest first.
     *
     * @param user the owning user
     * @return the user's unread notifications, newest first
     */
    List<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(User user);

    /**
     * Find a notification by id only when it belongs to the given user.
     * Used for mark-as-read and delete operations so a notification id can
     * never be targeted across users.
     *
     * @param id   notification primary key
     * @param user the owning user
     * @return the notification when owned by the user, empty otherwise
     */
    Optional<Notification> findByIdAndUser(Long id, User user);

    /**
     * True when the given user already has a notification for the given
     * deterministic source key. Used by the generation service so each
     * rule evaluation is idempotent: the same logical event never creates a
     * duplicate notification.
     *
     * @param user      the owning user
     * @param sourceKey the deterministic key of the event
     * @return true when a notification for that event already exists
     */
    boolean existsByUserAndSourceKey(User user, String sourceKey);

    /**
     * The number of the given user's unread notifications. Drives the navbar
     * bell badge. Scoped to the owning user so the count is always this
     * user's only.
     *
     * @param user the owning user
     * @return the number of unread notifications
     */
    long countByUserAndReadFalse(User user);

    /**
     * The given user's newest notifications, limited by the provided
     * pageable size. Used for the navbar dropdown (a compact recent list)
     * without loading the full history.
     *
     * @param user     the owning user
     * @param pageable controls the page size (limit); ordering is newest first
     * @return up to the requested number of the user's newest notifications
     */
    List<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * Mark every one of the given user's unread notifications as read.
     * A single user-scoped bulk update — never a per-notification loop.
     *
     * @param user the owning user
     * @return the number of notifications that were flipped to read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false")
    int markAllReadByUser(@Param("user") User user);

    /**
     * The number of READ notifications older than the given cut-off. Used to
     * report how many would be removed by a retention pass.
     *
     * Only {@code read = true} rows are eligible, so unread notifications —
     * however old — are never auto-removed (conservative retention).
     *
     * @param cutoff notifications created strictly before this are eligible
     * @return the count of eligible read notification rows
     */
    long countByReadTrueAndCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Conservative automatic retention: delete the user's READ notifications
     * older than the given cut-off. Unread notification rows are preserved.
     *
     * Only the Notification table is affected — never Users, Tasks, Exams,
     * StudySessions, Subjects or Topics.
     *
     * @param cutoff notifications created strictly before this are deleted
     * @return the number of notification rows removed
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
