package com.studyflow.studyflow.exception;

/**
 * Thrown when the current authenticated principal cannot be resolved to an
 * existing {@code User} record.
 *
 * This is a safe, non-{@code null} failure contract for the profile service:
 * callers never receive {@code null} (which would otherwise cascade into a
 * {@link NullPointerException}) and never leak SQL details, stack traces or
 * authentication internals.
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * @param message human-readable reason (no sensitive internals)
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}
