package com.studyflow.studyflow.exception;

/**
 * Thrown when a submitted settings update fails Bean Validation (e.g. a zero
 * or negative weekly goal, or an invalid focus duration).
 *
 * Keeps invalid data out of the database and distinguishes a business-rule
 * rejection from an unexpected runtime failure. No invalid update is ever
 * persisted.
 */
public class InvalidSettingsDataException extends RuntimeException {

    /**
     * @param message the validation message describing the rejected field
     */
    public InvalidSettingsDataException(String message) {
        super(message);
    }
}