package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.DashboardData;

/**
 * Service that builds the authenticated USER dashboard.
 *
 * The controller stays thin and delegates all aggregation to this service,
 * which composes data from the authenticated user, the existing subject
 * service and the setup checklist.
 */
public interface DashboardService {

    /**
     * Build the dashboard data for the currently authenticated user.
     *
     * @param userEmail the authenticated user's email (from the security context)
     * @return dashboard data assembled from real application state
     */
    DashboardData getDashboardData(String userEmail);
}
