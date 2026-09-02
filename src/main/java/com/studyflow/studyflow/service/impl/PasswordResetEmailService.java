package com.studyflow.studyflow.service.impl;

/**
 * Minimal email service scoped exclusively to password reset.
 * <p>
 * Delegates to Spring's {@link org.springframework.mail.javamail.JavaMailSender}
 * and constructs a simple HTML reset email.
 * <p>
 * SMTP credentials are configured via application.properties — never hard-coded.
 */
public interface PasswordResetEmailService {

    /**
     * Send a password-reset email to the given address.
     *
     * @param toEmail  the recipient's email address
     * @param rawToken the raw (unhashed) reset token to include in the link
     */
    void sendPasswordResetEmail(String toEmail, String rawToken);
}
