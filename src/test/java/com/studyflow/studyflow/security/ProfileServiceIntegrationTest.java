package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sub-Phase B integration tests (live database, transactional rollback).
 *
 * Exercises {@link ProfileService} end-to-end: the current user is resolved
 * from the Spring Security context, profile reads/updates hit the real
 * {@code users} table, name updates persist and survive a reload, another
 * user's row is never touched (isolation), and login identity / role are
 * preserved.
 */
@SpringBootTest
@Transactional
class ProfileServiceIntegrationTest {

    private static final String ALICE_EMAIL = "pf-a@example.com";
    private static final String BOB_EMAIL = "pf-b@example.com";

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private void authenticateAs(String email) {
        org.springframework.security.core.userdetails.User details =
                new org.springframework.security.core.userdetails.User(
                        email, "password123", List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "password123",
                        details.getAuthorities()));
    }

    @Test
    void getProfile_returnsCurrentAuthenticatedUser() {
        User alice = saveUser(ALICE_EMAIL);
        authenticateAs(ALICE_EMAIL);

        ProfileDto dto = profileService.getCurrentUserProfile();

        assertEquals(alice.getId(), userRepository.findByEmail(ALICE_EMAIL).orElseThrow().getId());
        assertEquals(ALICE_EMAIL, dto.email());
        assertEquals(ALICE_EMAIL, dto.name());
    }

    @Test
    void updateName_persistsInDatabase_andSurvivesReload() {
        saveUser(ALICE_EMAIL);
        authenticateAs(ALICE_EMAIL);

        ProfileDto updated = profileService.updateCurrentUserProfile(
                new ProfileDto("Iffat Jahan Updated", ALICE_EMAIL));

        assertEquals("Iffat Jahan Updated", updated.name());

        // Reload from the real database: the change must have persisted.
        User reloaded = userRepository.findByEmail(ALICE_EMAIL).orElseThrow();
        assertEquals("Iffat Jahan Updated", reloaded.getName());
    }

    @Test
    void updateName_userIsolation_neverTouchesAnotherUser() {
        saveUser(ALICE_EMAIL);
        saveUser(BOB_EMAIL);
        authenticateAs(ALICE_EMAIL);

        profileService.updateCurrentUserProfile(new ProfileDto("Alice Renamed", ALICE_EMAIL));

        User bob = userRepository.findByEmail(BOB_EMAIL).orElseThrow();
        assertEquals(BOB_EMAIL, bob.getName(), "Bob's profile must remain unchanged");
        assertEquals(Role.USER, bob.getRole());
    }

    @Test
    void updateProfile_emailAndRoleAndHash_areNeverPersisted() {
        User alice = saveUser(ALICE_EMAIL);
        String originalHash = alice.getPassword();
        authenticateAs(ALICE_EMAIL);

        profileService.updateCurrentUserProfile(
                new ProfileDto("Alice", "attacker@evil.com"));

        User reloaded = userRepository.findByEmail(ALICE_EMAIL).orElseThrow();
        assertEquals(ALICE_EMAIL, reloaded.getEmail(), "login email must not change");
        assertEquals(Role.USER, reloaded.getRole(), "role must stay USER");
        assertEquals(originalHash, reloaded.getPassword(), "password hash must stay untouched");
    }

    @Test
    void updateProfile_blankName_isRejectedAndNotPersisted() {
        saveUser(ALICE_EMAIL);
        authenticateAs(ALICE_EMAIL);

        assertThrows(InvalidProfileDataException.class,
                () -> profileService.updateCurrentUserProfile(new ProfileDto(" ", ALICE_EMAIL)));

        User reloaded = userRepository.findByEmail(ALICE_EMAIL).orElseThrow();
        assertEquals(ALICE_EMAIL, reloaded.getName(), "blank name must not overwrite stored name");
    }

    @Test
    void getProfile_authenticatedPrincipalWithNoUserRow_throwsSafeException() {
        authenticateAs("ghost@example.com");

        assertThrows(UserNotFoundException.class,
                profileService::getCurrentUserProfile);
    }
}