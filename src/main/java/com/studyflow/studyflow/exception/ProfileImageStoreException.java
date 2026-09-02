package com.studyflow.studyflow.exception;

/**
 * Thrown when a profile picture cannot be stored or deleted due to a
 * filesystem/IO failure (not a validation rejection).
 *
 * Distinct from {@link InvalidImageException}: reaching this exception means
 * the submitted image was valid but the server could not complete the file
 * operation, so the user is given a "please try again" message instead of an
 * internal error page.
 */
public class ProfileImageStoreException extends RuntimeException {

    /**
     * @param message user-facing reason
     */
    public ProfileImageStoreException(String message) {
        super(message);
    }

    /**
     * @param message user-facing reason
     * @param cause    the underlying IO failure
     */
    public ProfileImageStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}