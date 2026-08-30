package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.AnalyticsDto;

/**
 * Produces study analytics for the authenticated user.
 *
 * Ownership is enforced through the subject chain (User → Subject →
 * StudySession): every query is resolved from the security-context email
 * and scoped to that user, so analytics never observe another user's
 * sessions.
 */
public interface AnalyticsService {

    /**
     * Build the analytics snapshot for the given authenticated user.
     *
     * @param userEmail the authenticated user's email
     * @return the user's analytics, with honest zeroes when there is no
     *         recorded study activity
     */
    AnalyticsDto getAnalytics(String userEmail);
}