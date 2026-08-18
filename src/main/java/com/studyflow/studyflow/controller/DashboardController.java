package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller responsible for the authenticated user's dashboard.
 *
 * The route is protected by Spring Security, so the Authentication object
 * is always available here. The controller stays thin: it resolves the
 * authenticated user's email and delegates all dashboard aggregation to
 * the {@link DashboardService}.
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Constructor injection of the dashboard service.
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Display the personalized dashboard for the authenticated user.
     *
     * @param authentication current authenticated principal (email = username)
     * @param model          view model
     * @return the dashboard template name
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {

        // The security context is the single source of truth; never trust
        // client-supplied identifiers.
        String userEmail = authentication.getName();

        model.addAttribute("data", dashboardService.getDashboardData(userEmail));

        return "dashboard";
    }
}
