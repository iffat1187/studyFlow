package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Aggregated statistics backing the ADMIN dashboard.
 *
 * Every value is produced by the admin service layer from the
 * repositories — nothing is hard-coded. Task and Exam statistics
 * are intentionally absent because those modules do not exist yet.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardData {

    /**
     * Total number of registered users.
     */
    private long totalUsers;

    /**
     * Total number of subjects in the platform.
     */
    private long totalSubjects;

    /**
     * Number of users registered during the current calendar month.
     */
    private long newUsersThisMonth;

    /**
     * Number of users with ROLE_USER.
     */
    private long userCount;

    /**
     * Number of users with ROLE_ADMIN.
     */
    private long adminCount;

    /**
     * Role labels ("USER", "ADMIN") for the role distribution chart.
     */
    private List<String> roleLabels;

    /**
     * Role counts aligned positionally with {@link #roleLabels}.
     */
    private List<Long> roleCounts;

    /**
     * Month labels ("MMM yyyy") for the registration trend chart.
     */
    private List<String> trendLabels;

    /**
     * Registration counts aligned positionally with {@link #trendLabels}.
     */
    private List<Long> trendCounts;

    /**
     * The five most recently registered users.
     */
    private List<User> recentUsers;
}