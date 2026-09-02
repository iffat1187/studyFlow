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
     * <p>
     * Platform totals are global database counts. The dashboard's
     * "Complete Your StudyFlow" onboarding cards are scoped to the
     * authenticated admin's OWN user-owned records, so the section
     * always reflects the signed-in admin and never another user's data.
     *
     * @param adminEmail email of the authenticated admin (security context)
     * @return aggregated dashboard data built from real database values
     */
    AdminDashboardData getDashboardData(String adminEmail);
}