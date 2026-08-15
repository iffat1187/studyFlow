package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom UserDetailsService that bridges StudyFlow's User entity
 * to Spring Security's UserDetails.
 *
 * For StudyFlow the Spring Security "username" is the user's email address.
 *
 * Responsibilities:
 * - Look up a user by email via UserRepository.findByEmail(...).
 * - Throw UsernameNotFoundException when no user exists.
 * - Convert the stored User entity into Spring Security UserDetails
 *   using the already-encoded password (never re-encode).
 * - Map the entity Role to a Spring Security ROLE_ authority.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Constructor injection of the existing UserRepository.
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads the user by their login identifier.
     * For StudyFlow the identifier is the user's email address.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for the provided email address"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
