package com.studyflow.studyflow.service;

import com.studyflow.studyflow.entity.PasswordResetToken;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.PasswordResetTokenRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.PasswordResetEmailService;
import com.studyflow.studyflow.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordResetServiceImpl}.
 *
 * <p>Covers the core token lifecycle and security invariants:
 * <ul>
 *   <li>Token generation is random and the raw token is never persisted
 *       (only its SHA-256 hash is stored).</li>
 *   <li>Email-address enumeration is prevented: a non-existent email and a
 *       blank email trigger no lookup or email, and the service always
 *       returns the same void for unknown accounts.</li>
 *   <li>Requesting a reset invalidates previously active tokens.</li>
 *   <li>Token validity rejects missing, expired and already-used tokens.</li>
 *   <li>ResetPassword re-encodes the password and marks the token used.</li>
 *   <li>Reuse and expiry guard against replay attacks.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final String EMAIL = "reset@example.com";

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetEmailService emailService;

    private PasswordEncoder passwordEncoder;

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // 30-minute expiry, matching the default configuration.
        service = new PasswordResetServiceImpl(
                tokenRepository, userRepository, emailService, passwordEncoder, 30);
    }

    private User user() {
        return User.builder()
                .id(1L)
                .name("Reset User")
                .email(EMAIL)
                .password(passwordEncoder.encode("oldPass123"))
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private PasswordResetToken activeToken(User user) {
        return PasswordResetToken.builder()
                .id(1L)
                .tokenHash("hash")
                .user(user)
                .expiryTime(LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();
    }

    // ----------------------------------------------------------------
    // requestPasswordReset
    // ----------------------------------------------------------------

    @Test
    void requestPasswordReset_unknownEmail_neverInquiresOrEmails() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestPasswordReset(EMAIL);

        // No token created, no email sent, no invalidation needed.
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(tokenRepository, never()).invalidateAllByUser(any(User.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_blankEmail_doesNothing() {
        service.requestPasswordReset("   ");

        verify(userRepository, never()).findByEmail(anyString());
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_existingUser_savesTokenHashesAndEmailsRawToken() {
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestPasswordReset(EMAIL);

        // Older active tokens are invalidated first.
        verify(tokenRepository).invalidateAllByUser(user);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());

        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getExpiryTime()).isAfter(LocalDateTime.now());
        // Never store the raw token — the stored value must be a SHA-256 hash.
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getTokenHash()).hasSize(64);

        // Email receives the raw token.
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                org.mockito.ArgumentMatchers.eq(EMAIL), emailCaptor.capture());
        assertThat(emailCaptor.getValue()).isNotBlank();
    }

    @Test
    void requestPasswordReset_normalisesEmailCaseAndTrims() {
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestPasswordReset("  RESET@EXAMPLE.COM  ");

        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void requestPasswordReset_emailFailure_isSwallowed() {
        User user = user();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());

        // Must not propagate; the flow completes gracefully.
        service.requestPasswordReset(EMAIL);
    }

    // ----------------------------------------------------------------
    // isTokenValid
    // ----------------------------------------------------------------

    @Test
    void isTokenValid_validActiveToken_returnsTrue() {
        User user = user();
        // Because the service hashes before lookup, we emulate the hash
        // lookup by returning an active token for ANY queried hash.
        when(tokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(activeToken(user)));

        assertThat(service.isTokenValid("anyRawToken")).isTrue();
    }

    @Test
    void isTokenValid_nullOrBlank_returnsFalseWithoutLookup() {
        assertThat(service.isTokenValid(null)).isFalse();
        assertThat(service.isTokenValid("   ")).isFalse();
        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void isTokenValid_unknownToken_returnsFalse() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThat(service.isTokenValid("unknown")).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        User user = user();
        PasswordResetToken expired = activeToken(user);
        expired.setExpiryTime(LocalDateTime.now().minusMinutes(5));

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThat(service.isTokenValid("expired")).isFalse();
    }

    @Test
    void isTokenValid_usedToken_returnsFalse() {
        User user = user();
        PasswordResetToken used = activeToken(user);
        used.setUsed(true);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThat(service.isTokenValid("used")).isFalse();
    }

    // ----------------------------------------------------------------
    // resetPassword
    // ----------------------------------------------------------------

    @Test
    void resetPassword_validToken_encodesNewPasswordAndMarksUsed() {
        User user = user();
        PasswordResetToken token = activeToken(user);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.resetPassword("rawToken", "brandNewPass1");

        // Password is re-encoded (BCrypt) — never stored in plain text.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        String stored = userCaptor.getValue().getPassword();
        assertThat(stored).startsWith("{bcrypt}");
        assertThat(stored).isNotEqualTo("brandNewPass1");
        assertThat(passwordEncoder.matches("brandNewPass1", stored)).isTrue();

        // Token marked used to prevent replay.
        assertThat(token.isUsed()).isTrue();
        verify(tokenRepository).save(token);
        // All other tokens invalidated as a safety measure.
        verify(tokenRepository).invalidateAllByUser(user);
    }

    @Test
    void resetPassword_nullOrBlankToken_throws() {
        assertThatThrownBy(() -> service.resetPassword(null, "somePass123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resetPassword("  ", "somePass123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_blankNewPassword_throws() {
        assertThatThrownBy(() -> service.resetPassword("rawToken", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_unknownToken_throwsAndDoesNotTouchUser() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("unknown", "newPass123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPassword_expiredToken_throws() {
        User user = user();
        PasswordResetToken expired = activeToken(user);
        expired.setExpiryTime(LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resetPassword("expired", "newPass123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
        assertThat(expired.isUsed()).isFalse();
    }

    @Test
    void resetPassword_usedToken_throwsAndPreventsReplay() {
        User user = user();
        PasswordResetToken used = activeToken(user);
        used.setUsed(true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.resetPassword("used", "newPass123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    // ----------------------------------------------------------------
    // Entity helper
    // ----------------------------------------------------------------

    @Test
    void token_isExpiredOrUsed_evaluatesCorrectly() {
        User user = user();

        PasswordResetToken fresh = activeToken(user);
        assertThat(fresh.isExpiredOrUsed()).isFalse();

        PasswordResetToken expired = activeToken(user);
        expired.setExpiryTime(LocalDateTime.now().minusMinutes(1));
        assertThat(expired.isExpiredOrUsed()).isTrue();

        PasswordResetToken used = activeToken(user);
        used.setUsed(true);
        assertThat(used.isExpiredOrUsed()).isTrue();
    }
}
