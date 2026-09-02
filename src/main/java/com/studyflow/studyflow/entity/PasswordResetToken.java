package com.studyflow.studyflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent token that authorises a one-time password reset.
 *
 * <p>The {@code token} column stores the <em>hashed</em> representation
 * (SHA-256) of the cryptographically random token that is included in the
 * reset email.  The raw token is <b>never</b> persisted.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Created when the user requests a reset.</li>
 *   <li>Marked {@code used = true} after a successful password change.</li>
 *   <li>Rejected when expired, already used, or absent.</li>
 * </ol>
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_prt_token_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_prt_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hex digest of the random token sent to the user.
     * The raw token is never stored.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * The user who requested the reset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Instant after which the token must be rejected.
     */
    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    /**
     * Whether this token has already been consumed. A consumed token
     * can never be used again.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;

    /**
     * Timestamp when this token was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * True when the token has already been used or has passed its expiry time.
     */
    public boolean isExpiredOrUsed() {
        return used || LocalDateTime.now().isAfter(expiryTime);
    }
}
