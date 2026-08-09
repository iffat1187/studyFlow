package com.studyflow.studyflow.entity;

/**
 * Enum representing the roles a user can have in the application.
 *
 * Stored in the database as a String via EnumType.STRING
 * so the stored values remain stable even if the enum
 * definition is reordered.
 */
public enum Role {

    /**
     * Standard authenticated user with access to the app.
     */
    USER,

    /**
     * Administrator with elevated permissions.
     */
    ADMIN
}