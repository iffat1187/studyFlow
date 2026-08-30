package com.studyflow.studyflow.entity;

/**
 * Enum representing the type of a study session.
 *
 * Stored in the database as a String via EnumType.STRING
 * so the stored values remain stable even if the enum
 * definition is reordered.
 */
public enum StudySessionType {

    /**
     * Deep, distraction-free focused study block.
     */
    FOCUS,

    /**
     * Reviewing previously learned material.
     */
    REVISION,

    /**
     * Solving problems or completing practice exercises.
     */
    PRACTICE,

    /**
     * Reading notes, books, articles or lecture material.
     */
    READING,

    /**
     * Working on an assignment.
     */
    ASSIGNMENT,

    /**
     * Preparing specifically for an upcoming exam.
     */
    EXAM_PREPARATION,

    /**
     * Generic/other session type.
     */
    OTHER
}