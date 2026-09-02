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
 * repositories — nothing is hard-coded. Platform totals are global
 * database counts, while the {@code setupCards} are scoped to the
 * authenticated admin's own user-owned records so one user's private
 * data is never surfaced to another.
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
     * Total number of topics in the platform.
     */
    private long totalTopics;

    /**
     * Total number of tasks in the platform.
     */
    private long totalTasks;

    /**
     * Total number of exams in the platform.
     */
    private long totalExams;

    /**
     * Total number of study sessions logged in the platform.
     */
    private long totalStudySessions;

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
     * Number of users with enabled accounts.
     */
    private long activeCount;

    /**
     * Number of users with disabled accounts.
     */
    private long disabledCount;

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

    /**
     * Onboarding cards for the authenticated admin: state derived from the
     * admin's own user-owned records (subjects, topics, tasks, exams and
     * study sessions), never from another user's data.
     */
    private List<AdminSetupCard> setupCards;
}