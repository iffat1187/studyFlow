package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.NotificationPreference;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link NotificationPreference}.
 *
 * Ownership is always scoped to a {@link User}: preferences are read and
 * written for the user resolved from the security context, never from the
 * browser. The unique {@code user_id} column plus a derived finder enforce
 * that a user has at most one preference record.
 */
@Repository
public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, Long> {

    /**
     * The single preference record for the given user, if any.
     *
     * @param user the owning user
     * @return the user's preference, or empty when none has been created
     */
    Optional<NotificationPreference> findByUser(User user);
}
