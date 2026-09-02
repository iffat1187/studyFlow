package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.AdminDashboardData;
import com.studyflow.studyflow.service.AdminDashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for the ADMIN dashboard.
 *
 * Route: GET /admin/dashboard
 *
 * Backend authorization is enforced by SecurityConfig: only ROLE_ADMIN
 * reaches this controller. The controller stays thin and delegates the
 * aggregation to AdminDashboardService, passing the authenticated admin's
 * identity so the onboarding cards are scoped to that admin's own data.
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * Constructor injection.
     */
    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * Display the admin dashboard with real platform statistics.
     *
     * @param authentication current authenticated principal (email = username)
     * @param model          view model
     * @return the admin dashboard template name
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {

        // The security context is the single source of truth; never trust
        // client-supplied identifiers.
        String adminEmail = authentication.getName();

        AdminDashboardData data = adminDashboardService.getDashboardData(adminEmail);
        model.addAttribute("data", data);
        return "admin/dashboard";
    }
}