package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of UserService.
 *
 * Contains the business logic for User operations and delegates
 * the persistence work to UserRepository.
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the UserRepository bean.
     */
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Create / save a user by delegating to the repository.
     */
    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Find a user by email by delegating to the repository.
     */
    @Override
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check whether an email already exists by delegating to the repository.
     */
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}