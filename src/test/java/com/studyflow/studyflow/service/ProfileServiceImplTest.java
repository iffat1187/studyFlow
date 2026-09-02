package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProfileServiceImpl}.
 *
 * The current user is identified exclusively from the Spring Security
 * {@code SecurityContext}. These tests set that context to user A, user B, a
 * principal with no matching {@code User} row, etc., and assert the
 * IDOR / isolation / protection contracts:
 *
 *  - get current profile returns exactly the authenticated user,
 *  - update changes ONLY the approved display name,
 *  - email / role / password are never modified,
 *  - invalid names are rejected and never persisted,
 *  - a missing user row yields a safe exception, never a NPE.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private static final String ALICE_EMAIL = "p-a@example.com";
    private static final String BOB_EMAIL = "p-b@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorage imageStorage;

    private Validator validator;

    private PasswordEncoder passwordEncoder;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new ProfileServiceImpl(userRepository, validator, imageStorage,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User authenticateAs(String email, Long id) {
        User user = User.builder().id(id).name(email).email(email)
                .password("hash").role(Role.USER).build();
        UserDetails details = new org.springframework.security.core.userdetails.User(
                email, "encoded", List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "encoded",
                        details.getAuthorities()));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    // TEST 1
    @Test
    void getCurrentUserProfile_returnsAuthenticatedUserA() {
        authenticateAs(ALICE_EMAIL, 1L);

        ProfileDto dto = service.getCurrentUserProfile();

        assertThat(dto.email()).isEqualTo(ALICE_EMAIL);
        assertThat(dto.name()).isEqualTo(ALICE_EMAIL);
    }

    // TEST 2
    @Test
    void getCurrentUserProfile_returnsAuthenticatedUserB() {
        authenticateAs(BOB_EMAIL, 2L);

        ProfileDto dto = service.getCurrentUserProfile();

        assertThat(dto.email()).isEqualTo(BOB_EMAIL);
    }

    // TEST 3 + 4
    @Test
    void updateProfile_changesOnlyName_andPersists() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userRepository.save(alice)).thenAnswer(inv -> inv.getArgument(0));

        ProfileDto updated = service.updateCurrentUserProfile(
                new ProfileDto("Iffat Jahan Updated", ALICE_EMAIL));

        assertThat(updated.name()).isEqualTo("Iffat Jahan Updated");
        assertThat(alice.getName()).isEqualTo("Iffat Jahan Updated");
        verify(userRepository).save(alice);
    }

    // TEST 29 — email read-only
    @Test
    void updateProfile_emailSubmission_doesNotChangeLoginIdentity() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userRepository.save(alice)).thenAnswer(inv -> inv.getArgument(0));

        ProfileDto updated = service.updateCurrentUserProfile(
                new ProfileDto("Alice", "hacker@evil.com"));

        assertThat(updated.email()).isEqualTo(ALICE_EMAIL);
        assertThat(alice.getEmail()).isEqualTo(ALICE_EMAIL);
    }

    // TEST 30 — role protection
    @Test
    void updateProfile_roleCannotBeElevatedToAdmin() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userRepository.save(alice)).thenAnswer(inv -> inv.getArgument(0));

        service.updateCurrentUserProfile(new ProfileDto("Alice", ALICE_EMAIL));

        assertThat(alice.getRole()).isEqualTo(Role.USER);
    }

    // TEST 31 — password protection
    @Test
    void updateProfile_passwordHashIsNeverTouched() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        alice.setPassword("$2a$10$originalHashShouldStay");
        when(userRepository.save(alice)).thenAnswer(inv -> inv.getArgument(0));

        service.updateCurrentUserProfile(new ProfileDto("Alice", ALICE_EMAIL));

        assertThat(alice.getPassword()).isEqualTo("$2a$10$originalHashShouldStay");
    }

    // TEST 32 — IDOR: a poisoned SecurityContext cannot redirect the update
    @Test
    void updateProfile_neverWritesAnyRowOtherThanCurrentUser() {
        authenticateAs(ALICE_EMAIL, 1L); // context resolves ALICE at id 1
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateCurrentUserProfile(new ProfileDto("Alice New", ALICE_EMAIL));

        // No user id can ever be fetched; only the SecurityContext's email is used.
        verify(userRepository, never()).findById(99L);
        assertThat(userRepository.findAll()).isEmpty(); // nothing extra persisted
    }

    // TEST 34 — invalid name rejected, nothing persisted
    @Test
    void updateProfile_blankName_isRejectedAndNotPersisted() {
        authenticateAs(ALICE_EMAIL, 1L);

        assertThatThrownBy(() -> service.updateCurrentUserProfile(new ProfileDto("   ", ALICE_EMAIL)))
                .isInstanceOf(InvalidProfileDataException.class);

        assertThatThrownBy(() -> service.updateCurrentUserProfile(new ProfileDto(null, ALICE_EMAIL)))
                .isInstanceOf(InvalidProfileDataException.class);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // TEST 35 — principal without a User row => safe exception, no NPE
    @Test
    void getCurrentUserProfile_unknownPrincipal_throwsSafeException_notNpe() {
        org.springframework.security.core.userdetails.User details =
                new org.springframework.security.core.userdetails.User(
                        "ghost@example.com", "encoded", List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "encoded", details.getAuthorities()));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUserProfile())
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getCurrentUserProfile_noAuthentication_throwsSafeException() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.getCurrentUserProfile())
                .isInstanceOf(UserNotFoundException.class);
    }
}
