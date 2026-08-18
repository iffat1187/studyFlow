package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.service.AdminUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for the ADMIN user-management area.
 *
 * Routes:
 * - GET  /admin/users        -> user list with search + role filter
 * - GET  /admin/users/{id}   -> user details
 * - POST /admin/users/{id}/enable  -> toggle enabled status
 * - POST /admin/users/{id}/role    -> change role
 * - POST /admin/users/{id}/delete  -> delete account
 *
 * Backend authorization is enforced by SecurityConfig: only ROLE_ADMIN
 * reaches this controller. Business rules (self-protection, safe delete)
 * live in AdminUserService. Pages navigate with PRG and flash toasts.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * Constructor injection.
     */
    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * List users with an optional name/email search term and role filter.
     */
    @GetMapping
    public String userList(@RequestParam(value = "search", required = false) String search,
                           @RequestParam(value = "role", required = false) Role role,
                           Model model) {
        List<User> users = adminUserService.searchUsers(search, role);
        model.addAttribute("users", users);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("filterRole", role);
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    /**
     * Show a single user's details.
     */
    @GetMapping("/{id}")
    public String userDetails(@PathVariable Long id, Model model) {
        User user = adminUserService.findUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        model.addAttribute("adminUser", user);
        model.addAttribute("roles", Role.values());
        return "admin/user-details";
    }

    /**
     * Toggle a user's enabled status.
     *
     * PRG: always redirects; success/blocked outcomes are reported with
     * flash attributes (toast).
     */
    @PostMapping("/{id}/enable")
    public String toggleEnabled(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String message = adminUserService.toggleEnabled(id, authentication.getName());
        flashOutcome(redirectAttributes, message, "You cannot change your own account status.");
        return "redirect:/admin/users/{id}";
    }

    /**
     * Change a user's role.
     */
    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam("newRole") Role newRole,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String message = adminUserService.changeRole(id, newRole, authentication.getName());
        flashOutcome(redirectAttributes, message, "You cannot change your own account status.");
        return "redirect:/admin/users/{id}";
    }

    /**
     * Delete a user account.
     *
     * Blocked for the acting admin's own account via the service layer.
     */
    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        String message = adminUserService.deleteUser(id, authentication.getName());
        flashOutcome(redirectAttributes, message, "You cannot delete your own account.");
        return "redirect:/admin/users";
    }

    /**
     * Flash a success toast when the service returned a message, otherwise
     * flash an error toast with the given block reason.
     */
    private void flashOutcome(RedirectAttributes redirectAttributes,
                              String message,
                              String blockReason) {
        if (message == null) {
            redirectAttributes.addFlashAttribute("errorMessage", blockReason);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", message);
        }
    }

    /**
     * Expected business errors (e.g. a user id that no longer exists)
     * are surfaced as a safe toast instead of a Whitelabel page.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleExpectedError(IllegalArgumentException exception,
                                      RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(
                "errorMessage", "Unable to complete the requested action.");
        return "redirect:/admin/users";
    }
}