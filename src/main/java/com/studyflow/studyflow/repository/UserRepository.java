package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their email address.
     * Used by future authentication to resolve a user during login.
     *
     * @param email the email address to search for
     * @return an Optional containing the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether a user exists with the given email address.
     * Used by future registration to detect duplicate emails.
     *
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Count users per role, for the admin role distribution chart.
     *
     * @return rows of (Role, count)
     */
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countUsersByRole();

    /**
     * Count registrations grouped by calendar year-month, for the admin
     * registration trend chart. Rows without a creation timestamp are
     * excluded.
     *
     * @return rows of (yyyy-MM, count)
     */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m'), COUNT(*) " +
            "FROM users WHERE created_at IS NOT NULL " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m') " +
            "ORDER BY DATE_FORMAT(created_at, '%Y-%m')",
            nativeQuery = true)
    List<Object[]> countRegistrationsByMonth();

    /**
     * Count users registered since the given instant.
     *
     * @param since lower bound (inclusive)
     * @return the number of users registered on or after {@code since}
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt IS NOT NULL AND u.createdAt >= :since")
    long countUsersCreatedSince(@Param("since") LocalDateTime since);

    /**
     * The five most recently registered users.
     *
     * @return a list of at most five users, newest first
     */
    List<User> findTop5ByOrderByCreatedAtDesc();

    /**
     * All users, newest registered first. Backs the admin user list.
     *
     * @return all users
     */
    List<User> findAllByOrderByCreatedAtDesc();

    /**
     * Search users whose name or email contains the term (case-insensitive),
     * newest registered first.
     *
     * @param nameTerm term matched against name
     * @param emailTerm term matched against email
     * @return matching users
     */
    List<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String emailTerm, String nameTerm);

    /**
     * Users with the given role, newest registered first.
     *
     * @param role the role to filter by
     * @return matching users
     */
    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    /**
     * Users matching a role AND a name/email term (case-insensitive),
     * newest registered first.
     *
     * @param role the role to filter by
     * @param emailTerm term matched against email
     * @param roleAgain the same role (Spring Data OR-expansion)
     * @param nameTerm term matched against name
     * @return matching users
     */
    List<User> findByRoleAndEmailContainingIgnoreCaseOrRoleAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Role role, String emailTerm, Role roleAgain, String nameTerm);

    /**
     * Count users by their account status.
     *
     * @param enabled whether the accounts are enabled
     * @return the number of matching users
     */
    long countByEnabled(boolean enabled);

    /**
     * All active (enabled) users. Backs the scheduled notification job so
     * generation always covers every enabled account without ever touching
     * disabled ones.
     *
     * @return all enabled users
     */
    List<User> findByEnabledTrue();
}