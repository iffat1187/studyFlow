package com.studyflow.studyflow.entity;

/**
 * Enum representing the type/category of an exam.
 *
 * Stored in the database as a String via EnumType.STRING
 * so the stored values remain stable even if the enum
 * definition is reordered.
 */
public enum ExamType {

    /**
     * Midterm examination.
     */
    MIDTERM,

    /**
     * Final examination.
     */
    FINAL,

    /**
     * Laboratory test.
     */
    LAB_TEST,

    /**
     * Laboratory final examination.
     */
    LAB_FINAL,

    /**
     * Class test or unit test.
     */
    CLASS_TEST,

    /**
     * Viva voce or oral examination.
     */
    VIVA,

    /**
     * Quiz or short assessment.
     */
    QUIZ,

    /**
     * Presentation or project defense.
     */
    PRESENTATION,

    /**
     * Generic/other exam type.
     */
    OTHER
}