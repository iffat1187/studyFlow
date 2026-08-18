package com.studyflow.studyflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Renders the branded access-denied page.
 *
 * Route: GET /403
 *
 * The route is intentionally public: it is the safe, no-internals page an
 * authenticated-but-unauthorized user is taken to. The real authorization
 * remains backend-enforced by SecurityConfig's /admin/** → ADMIN rule.
 * Filter-level denials additionally resolve via Boot's error handling to
 * the same template (error/403.html).
 */
@Controller
public class AccessDeniedController {

    @GetMapping("/403")
    public String accessDenied() {
        return "error/403";
    }
}