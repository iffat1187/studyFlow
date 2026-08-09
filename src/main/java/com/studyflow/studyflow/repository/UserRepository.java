package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}