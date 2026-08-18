package com.studyflow.studyflow.dto;

/**
 * A single step of the StudyFlow getting-started checklist.
 *
 * The status drives both the visual state and the progress calculation.
 * {@link Status#COMPLETED} counts towards setup progress, {@link Status#CURRENT}
 * is the next actionable step, and {@link Status#UPCOMING} marks steps that
 * depend on modules which have not been implemented yet.
 */
public record SetupStep(
        int number,
        String label,
        String icon,
        Status status,
        String href
) {

    /**
     * Visual / progress state of a setup step.
     */
    public enum Status {
        COMPLETED,
        CURRENT,
        UPCOMING
    }
}
