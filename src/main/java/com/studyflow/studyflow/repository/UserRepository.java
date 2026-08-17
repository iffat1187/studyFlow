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
}