package com.studyflow.studyflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the PasswordEncoder foundation shared by registration and
 * future authentication.
 *
 * Uses the same bean definition as SecurityConfig so production and test
 * behavior stay in sync.
 */
class PasswordEncoderTest {

    private PasswordEncoder encoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Test
    void encodedPassword_isBcryptPrefix_andNotEqualToRaw() {
        PasswordEncoder encoder = encoder();
        String raw = "password123";
        String encoded = encoder.encode(raw);

        assertNotEquals(raw, encoded);
        assertTrue(encoded.startsWith("{bcrypt}$2a$"));
    }

    @Test
    void matches_correctRawPassword_returnsTrue() {
        PasswordEncoder encoder = encoder();
        String raw = "password123";
        String encoded = encoder.encode(raw);

        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        PasswordEncoder encoder = encoder();
        String encoded = encoder.encode("password123");

        assertFalse(encoder.matches("wrong-password", encoded));
    }

    @Test
    void delegatingEncoder_acceptsPrefixedStoredPassword() {
        // Mirrors the shape of a registration-stored password
        // ({bcrypt}$2a$10$...) produced by the delegating encoder.
        PasswordEncoder encoder = encoder();
        String raw = "secretValue";
        String stored = encoder.encode(raw);

        assertTrue(stored.startsWith("{bcrypt}$2a$"));
        assertTrue(encoder.matches(raw, stored));
    }
}

