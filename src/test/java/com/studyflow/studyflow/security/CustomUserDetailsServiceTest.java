package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

    private User user(Long id, String email, String password, Role role) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);
        return user;
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        String encodedPassword = "$2a$10$abcdefghijklmnopqrstuv";
        User user = user(1L, "test@example.com", encodedPassword, Role.USER);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("test@example.com");

        assertEquals("test@example.com", details.getUsername());
        assertEquals(encodedPassword, details.getPassword());
        assertTrue(details.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void loadUserByUsername_nonexistentUser_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("doesnotexist@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("doesnotexist@example.com"));

        verify(userRepository).findByEmail("doesnotexist@example.com");
    }

    @Test
    void loadUserByUsername_adminUser_returnsRoleAdmin() {
        User admin = user(2L, "admin@example.com", "encoded", Role.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        UserDetails details = service.loadUserByUsername("admin@example.com");

        assertTrue(details.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_disabledUser_isMarkedDisabled() {
        User disabled = user(3L, "disabled@example.com", "encoded", Role.USER);
        disabled.setEnabled(false);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabled));

        UserDetails details = service.loadUserByUsername("disabled@example.com");

        assertFalse(details.isEnabled());
    }
}
