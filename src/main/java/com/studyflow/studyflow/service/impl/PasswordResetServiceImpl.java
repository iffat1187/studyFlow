package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.entity.PasswordResetToken;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.PasswordResetTokenRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Implementation of {@link PasswordResetService}.
 *
 * <p>Token generation uses {@link SecureRandom} to produce 32
 * cryptographically strong random bytes (256 bits of entropy),
 * hex-encoded into a 64-character string.  The raw token is
 * <b>never</b> stored; only its SHA-256 hash is persisted.</p>
 *
 * <p>Password encoding reuses the application's existing
 * {@link org.springframework.security.crypto.password.PasswordEncoder}
 * (BCrypt via DelegatingPasswordEncoder).</p>
 *
 * <p>Rate-limiting is implemented as a simple mechanism: if an
 * active (unused, non-expired) token already exists for the
 * requested user, the previous tokens are invalidated and a new
 * one is created.</p>
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordResetEmailService emailService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final int tokenExpiryMinutes;

    /**
     * Constructor injection.
     */
    public PasswordResetServiceImpl(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordResetEmailService emailService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            @Value("${studyflow.password-reset.token-expiry-minutes:30}") int tokenExpiryMinutes
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            log.info("Password reset email requested.");
            return;
        }

        String normalised = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalised);

        if (userOpt.isEmpty()) {
            log.info("Password reset email requested.");
            return;
        }

        User user = userOpt.get();

        // Invalidate any previous unused tokens for this user.
        tokenRepository.invalidateAllByUser(user);

        // Generate and persist a new token.
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiryTime(LocalDateTime.now().plusMinutes(tokenExpiryMinutes))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        log.info("Password reset email requested.");

        // Send email; failures are logged but never propagated to the caller.
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        } catch (Exception e) {
            log.error("Failed to send password reset email.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String hash = hashToken(rawToken);
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByTokenHash(hash);
        return tokenOpt.isPresent() && !tokenOpt.get().isExpiredOrUsed();
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token is missing.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        String hash = hashToken(rawToken);
        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByTokenHash(hash);

        if (tokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.isExpiredOrUsed()) {
            throw new IllegalArgumentException("Invalid or expired reset token.");
        }

        // Resolve the user.
        User user = resetToken.getUser();

        // Encode the new password using the existing PasswordEncoder (BCrypt).
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used — prevents reuse.
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Invalidate all other tokens for this user as a safety measure.
        tokenRepository.invalidateAllByUser(user);

        log.info("Password successfully reset via token.");
    }

    /**
     * Generate a cryptographically secure random token.
     * Uses {@link SecureRandom} for 32 bytes of entropy,
     * hex-encoded to a 64-character string.
     */
    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hash a raw token using SHA-256 before storage.
     * The raw token is never persisted.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM implementation.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
