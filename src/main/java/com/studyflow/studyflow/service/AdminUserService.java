package com.studyflow.studyflow.service;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Admin-scoped user management service.
 *
 * Contains the business rules for the admin user-management area only.
 * Self-protection (an admin cannot damage their own account) is enforced
 * here, and every operation is backend-authorized by Spring Security via
 * the /admin/** rule.
 */
public interface AdminUserService {

    /**
     * List users, optionally filtered by a name/email term and/or a role.
     *
     * @param searchTerm case-insensitive name/email term, may be null/blank
     * @param role       role filter, may be null for "all roles"
     * @return the matching users, newest registered first
     */
    List<User> searchUsers(String searchTerm, Role role);

    /**
     * Find a single user by id.
     *
     * @param id the user id
     * @return an Optional containing the user, or empty if not found
     */
    Optional<User> findUserById(Long id);

    /**
     * Toggle a user's enabled status.
     *
     * @param id             the target user id
     * @param actingAdminEmail email of the admin performing the action
     * @return a success message, or {@code null} when the action targets
     *         the acting admin themselves
     */
    String toggleEnabled(Long id, String actingAdminEmail);

    /**
     * Change a user's role.
     *
     * @param id               the target user id
     * @param newRole          the new role (USER or ADMIN)
     * @param actingAdminEmail email of the admin performing the action
     * @return a success message, or {@code null} when the acting admin
     *         tries to change their own role
     */
    String changeRole(Long id, Role newRole, String actingAdminEmail);

    /**
     * Delete a user account.
     *
     * Users have no relationships to other entities yet, so a safe hard
     * delete is used. Self-deletion is blocked.
     *
     * @param id               the target user id
     * @param actingAdminEmail email of the admin performing the action
     * @return a success message, or {@code null} when the acting admin
     *         tries to delete themselves
     */
    String deleteUser(Long id, String actingAdminEmail);
}