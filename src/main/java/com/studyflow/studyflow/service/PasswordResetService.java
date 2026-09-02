package com.studyflow.studyflow.service;

/**
 * Service interface for the Forgot Password / Password Reset feature.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Generate a cryptographically secure reset token.</li>
 *   <li>Hash the token before persisting.</li>
 *   <li>Send the reset link via email.</li>
 *   <li>Validate tokens (existence, expiry, single-use).</li>
 *   <li>Reset the user's password using the existing {@link org.springframework.security.crypto.password.PasswordEncoder}.</li>
 *   <li>Invalidate old tokens so only the latest link is usable.</li>
 * </ul>
 * <p>
 * The reset flow must never reveal whether an email address exists.
 * The same success response is returned regardless of account state.
 */
public interface PasswordResetService {

    /**
     * Request a password reset for the given email address.
     * <p>
     * Regardless of whether the email is registered:
     * <ol>
     *   <li>Any previous unused tokens for that user are invalidated.</li>
     *   <li>A new token is generated and persisted.</li>
     *   <li>An email with the reset link is sent (if the account exists).</li>
     * </ol>
     * <p>
     * Returns void; never reveals whether the email exists.
     *
     * @param email the email address submitted by the user
     */
    void requestPasswordReset(String email);

    /**
     * Validate the raw token string.
     *
     * @param rawToken the token from the email link
     * @return true when the token is valid, not expired and not yet used
     */
    boolean isTokenValid(String rawToken);

    /**
     * Reset the password for the user associated with the given token.
     *
     * @param rawToken    the token from the email link
     * @param newPassword the desired new password (plain text)
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    void resetPassword(String rawToken, String newPassword);
}
