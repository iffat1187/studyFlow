package com.studyflow.studyflow.exception;

/**
 * Thrown when a submitted profile update fails Bean Validation (e.g. a blank
 * or over-long display name).
 *
 * Keeps invalid data out of the database and distinguishes a business-rule
 * rejection from an unexpected runtime failure. No invalid update is ever
 * persisted.
 */
public class InvalidProfileDataException extends RuntimeException {

    /**
     * @param message the validation message describing the rejected field
     */
    public InvalidProfileDataException(String message) {
        super(message);
    }
}
