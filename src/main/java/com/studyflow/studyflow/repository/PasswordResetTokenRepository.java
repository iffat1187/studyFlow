package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.PasswordResetToken;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link PasswordResetToken} entities.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Look up a token record by its hashed value.
     *
     * @param tokenHash SHA-256 hex digest of the raw token
     * @return the matching token record, or empty
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Find the latest active (unused and non-expired) token for a user.
     * Used to check whether a cooldown or replacement is needed.
     *
     * @param user the token owner
     * @return an active token, or empty
     */
    Optional<PasswordResetToken> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    /**
     * Invalidate all active tokens for a user.
     * Called before creating a new token so that only the latest link is valid.
     *
     * @param user the token owner
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllByUser(User user);
}
