package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link UserSettings}.
 *
 * Ownership is always scoped to a {@link User}: settings are read and
 * written for the user resolved from the security context, never from the
 * browser. The unique {@code user_id} column plus a derived finder enforce
 * that a user has at most one settings record.
 */
@Repository
public interface UserSettingsRepository
        extends JpaRepository<UserSettings, Long> {

    /**
     * The single settings record for the given user, if any.
     *
     * @param user the owning user
     * @return the user's settings, or empty when none has been created
     */
    Optional<UserSettings> findByUser(User user);
}