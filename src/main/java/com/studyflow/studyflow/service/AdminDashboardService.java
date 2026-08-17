package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.AdminDashboardData;

/**
 * Service that aggregates platform-wide statistics for the ADMIN dashboard.
 *
 * The controller stays thin and delegates all repository access to this
 * service, which centralizes the admin queries.
 */
public interface AdminDashboardService {

    /**
     * Build the statistics required by the admin dashboard.
     *
     * @return aggregated dashboard data built from real database values
     */
    AdminDashboardData getDashboardData();
}