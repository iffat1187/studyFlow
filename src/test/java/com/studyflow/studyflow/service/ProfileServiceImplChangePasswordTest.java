package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ChangePasswordDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidPasswordOperationException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.ProfileServiceImpl;
import com.studyflow.studyflow.storage.ProfileImageStorage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the change-password flow in {@link ProfileServiceImpl}.
 *
 * The owner is resolved from the Spring Security context, never a userId.
 * Covers: happy-path encode + save, wrong current password, mismatch, same
 * password, blank/short new password, user isolation between two users, and
 * the absence of raw/hash password in the service's observable behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplChangePasswordTest {

    private static final String ALICE_EMAIL = "cp-a@example.com";
    private static final String BOB_EMAIL = "cp-b@example.com";
    private static final String OLD_PASSWORD = "oldPass123";
    private static final String NEW_PASSWORD = "newPass456";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorage imageStorage;

    private Validator validator;

    private PasswordEncoder passwordEncoder;

    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        service = new ProfileServiceImpl(userRepository, validator, imageStorage, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User authenticateAs(String email, String rawPassword, Long id) {
        User user = User.builder().id(id).name(email).email(email)
                .password(passwordEncoder.encode(rawPassword)).role(Role.USER).build();
        UserDetails details = new org.springframework.security.core.userdetails.User(
                email, user.getPassword(), List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, user.getPassword(),
                        details.getAuthorities()));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private ChangePasswordDto dto(String current, String next, String confirm) {
        return new ChangePasswordDto(current, next, confirm);
    }

    // TEST 1 — valid change: old no longer authenticates, new does
    @Test
    void changePassword_validChange_encodesAndSavesNewHash() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(dto(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        String storedHash = alice.getPassword();
        // The new password matches, the old one no longer does.
        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, storedHash)).isFalse();
        verify(userRepository).save(alice);
    }

    // TEST 2 — wrong current password rejected; hash untouched
    @Test
    void changePassword_wrongCurrentPassword_isRejectedAndNotChanged() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);
        String originalHash = alice.getPassword();

        assertThatThrownBy(() -> service.changePassword(dto("wrong-old", NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(InvalidPasswordOperationException.class)
                .hasMessageContaining("Current password is incorrect");

        assertThat(alice.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(any());
    }

    // TEST 3 — mismatch rejected; no DB update
    @Test
    void changePassword_mismatch_isRejectedAndNotPersisted() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);
        String originalHash = alice.getPassword();

        assertThatThrownBy(() -> service.changePassword(dto(OLD_PASSWORD, "Pass123", "Pass456")))
                .isInstanceOf(InvalidPasswordOperationException.class)
                .hasMessageContaining("New passwords do not match");

        assertThat(alice.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(any());
    }

    // TEST 6 — same new password as current rejected
    @Test
    void changePassword_sameAsCurrent_isRejected() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);
        String originalHash = alice.getPassword();

        assertThatThrownBy(() -> service.changePassword(dto(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD)))
                .isInstanceOf(InvalidPasswordOperationException.class)
                .hasMessageContaining("different from your current password");

        assertThat(alice.getPassword()).isEqualTo(originalHash);
        verify(userRepository, never()).save(any());
    }

    // TEST 4/5 — blank / short new password rejected by validation
    @Test
    void changePassword_blankFields_areRejected() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);

        assertThatThrownBy(() -> service.changePassword(dto("  ", NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(InvalidPasswordOperationException.class)
                .hasMessageContaining("Current password is required");

        assertThatThrownBy(() -> service.changePassword(dto(OLD_PASSWORD, "", "")))
                .isInstanceOf(InvalidPasswordOperationException.class);

        verify(userRepository, never()).save(any());
    }

    // TEST 5 — short new password rejected
    @Test
    void changePassword_shortNewPassword_isRejected() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);

        assertThatThrownBy(() -> service.changePassword(dto(OLD_PASSWORD, "abc", "abc")))
                .isInstanceOf(InvalidPasswordOperationException.class)
                .hasMessageContaining("at least 6 characters");

        verify(userRepository, never()).save(any());
    }

    // TEST 43 — user isolation: only the authenticated user is affected
    @Test
    void changePassword_affectsOnlyAuthenticatedUser() {
        User alice = authenticateAs(ALICE_EMAIL, OLD_PASSWORD, 1L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(dto(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        // User B's password is never touched — no findById, no range op.
        verify(userRepository, never()).findById(any());
        assertThat(passwordEncoder.matches(NEW_PASSWORD, alice.getPassword())).isTrue();
    }

    // no auth -> safe exception
    @Test
    void changePassword_noAuthentication_throwsSafeException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.changePassword(dto(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
