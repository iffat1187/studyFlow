package com.studyflow.studyflow.exception;

/**
 * Thrown when a change-password request is rejected for a business-rule reason
 * other than Bean Validation (e.g. wrong current password, passwords do not
 * match, new password same as old).
 *
 * The message is safe for user-facing display — no internals, no hash, no
 * password content.
 */
public class InvalidPasswordOperationException extends RuntimeException {

    /**
     * @param message user-safe reason for the rejection
     */
    public InvalidPasswordOperationException(String message) {
        super(message);
    }
}
