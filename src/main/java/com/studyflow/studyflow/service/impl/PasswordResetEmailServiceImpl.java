package com.studyflow.studyflow.service.impl;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email service implementation scoped to password-reset emails only.
 *
 * <p>SMTP is optional: Spring Boot only auto-configures a
 * {@link JavaMailSender} when {@code spring.mail.host} is set.  This
 * service resolves the sender via {@link ObjectProvider} so the
 * application starts and functions normally even without SMTP
 * configuration.  When no mail sender is available, sending the reset
 * email is logged and skipped — the rest of the forgot-password flow
 * remains unaffected.  Credentials are configured via
 * application.properties, never hard-coded.</p>
 */
@Service
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String baseUrl;
    private final String fromAddress;
    private final String appName;

    public PasswordResetEmailServiceImpl(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${studyflow.base-url:http://localhost:8006}") String baseUrl,
            @Value("${studyflow.mail.from:no-reply@studyflow.app}") String fromAddress,
            @Value("${studyflow.app.name:StudyFlow}") String appName
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.baseUrl = baseUrl;
        this.fromAddress = fromAddress;
        this.appName = appName;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            // SMTP not configured. Skip gracefully so the flow still works.
            log.info("Email not configured (spring.mail.host not set); skipping password reset email to {}.",
                    toEmail);
            return;
        }

        String resetUrl = baseUrl + "/reset-password?token=" + rawToken;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, appName);
            helper.setTo(toEmail);
            helper.setSubject("Reset your " + appName + " password");

            helper.setText(buildEmailBody(resetUrl), true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email.", e);
        }
    }

    /**
     * Build a simple branded HTML email body.
     * Uses inline styles for maximum email-client compatibility.
     */
    private String buildEmailBody(String resetUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#0f172a;font-family:system-ui,-apple-system,sans-serif;">
                <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#0f172a;padding:40px 20px;">
                <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background-color:#1e293b;border-radius:16px;border:1px solid #334155;overflow:hidden;">
                <tr><td style="background:linear-gradient(135deg,#7c3aed,#9333ea,#a855f7);padding:32px 40px;text-align:center;">
                <h1 style="margin:0;color:#ffffff;font-size:28px;font-weight:700;">%s</h1>
                </td></tr>
                <tr><td style="padding:40px;">
                <h2 style="color:#e2e8f0;font-size:20px;margin:0 0 16px 0;">Reset your password</h2>
                <p style="color:#94a3b8;font-size:15px;line-height:1.6;margin:0 0 24px 0;">
                We received a request to reset the password for your account. Click the button below to choose a new password. This link will expire in 30 minutes.
                </p>
                <table cellpadding="0" cellspacing="0" style="margin:0 0 24px 0;">
                <tr><td style="background:#7c3aed;border-radius:10px;">
                <a href="%s" style="display:inline-block;padding:14px 32px;color:#ffffff;font-size:16px;font-weight:600;text-decoration:none;">Reset Password</a>
                </td></tr>
                </table>
                <p style="color:#64748b;font-size:13px;line-height:1.6;margin:0 0 8px 0;">
                If the button doesn't work, copy and paste this link into your browser:
                </p>
                <p style="word-break:break-all;margin:0 0 24px 0;">
                <a href="%s" style="color:#a78bfa;font-size:13px;">%s</a>
                </p>
                <hr style="border:none;border-top:1px solid #334155;margin:0 0 24px 0;">
                <p style="color:#475569;font-size:12px;line-height:1.6;margin:0;">
                If you did not request a password reset, you can safely ignore this email. Your password will not be changed unless you click the link above.
                </p>
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(appName, resetUrl, resetUrl, resetUrl);
    }
}
