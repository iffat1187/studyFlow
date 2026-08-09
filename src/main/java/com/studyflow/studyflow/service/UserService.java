package com.studyflow.studyflow.service;

import com.studyflow.studyflow.entity.User;

import java.util.Optional;

/**
 * Service interface for User operations.
 *
 * Defines the business operations required for authentication.
 * The implementation will be provided by UserServiceImpl.
 */
public interface UserService {

    /**
     * Create / save a user.
     *
     * @param user user entity to persist
     * @return the saved user information
     */
    User saveUser(User user);


    /**
     * Find a user by their email address.
     *
     * @param email the email address to search for
     * @return an Optional containing the user, or empty if not found
     */
    Optional<User> findUserByEmail(String email);


    /**
     * Check whether a user already exists with the given email address.
     *
     * @param email the email address to check
     * @return true if a user with this email exists, false otherwise
     */
    boolean existsByEmail(String email);

}