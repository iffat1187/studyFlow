package com.studyflow.studyflow.exception;

/**
 * Thrown when a submitted profile picture fails server-side validation:
 * empty file, over the size limit, unsupported MIME type, or content that
 * cannot be decoded as a real JPG/JPEG/PNG image.
 *
 * The message is user-facing ("Profile image must be ...") and never contains
 * filesystem internals or stack-trace noise.
 */
public class InvalidImageException extends RuntimeException {

    /**
     * @param message user-facing rejection reason
     */
    public InvalidImageException(String message) {
        super(message);
    }
}