package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.AdminDashboardData;
import com.studyflow.studyflow.service.AdminDashboardService;
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
 * aggregation to AdminDashboardService.
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
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        AdminDashboardData data = adminDashboardService.getDashboardData();
        model.addAttribute("data", data);
        return "admin/dashboard";
    }
}