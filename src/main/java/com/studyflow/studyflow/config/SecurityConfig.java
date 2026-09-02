package com.studyflow.studyflow.config;

import com.studyflow.studyflow.security.CustomUserDetailsService;
import com.studyflow.studyflow.security.RoleBasedAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Spring Security configuration for StudyFlow.
 *
 * Wires the existing authentication components together:
 *
 * AuthenticationProvider
 *      ├── CustomUserDetailsService  (loads users by email)
 *      └── PasswordEncoder           (BCrypt, handles password verification)
 *
 * The landing page, registration and login endpoints are public, while
 * the application's feature routes are protected. Sessions are used for
 * authentication. Logout is configured via Spring Security's built-in
 * mechanism. CSRF protection remains enabled.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final RoleBasedAuthenticationSuccessHandler authenticationSuccessHandler;

    /**
     * Constructor injection of the custom UserDetailsService and the
     * role-based authentication success handler.
     */
    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                          RoleBasedAuthenticationSuccessHandler authenticationSuccessHandler) {
        this.customUserDetailsService = customUserDetailsService;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    /**
     * Password encoder used to hash user passwords before storing them.
     *
     * The delegating factory uses BCrypt as its default encoding algorithm,
     * so freshly saved passwords are BCrypt-hashed. It also understands
     * the "{noop}" prefix used by the development user.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Authentication provider that combines the custom UserDetailsService
     * with the application's PasswordEncoder.
     *
     * StudyFlow uses the user's EMAIL as the login identifier, and the
     * CustomUserDetailsService resolves that email to a UserDetails object.
     * The provider then validates the submitted password against the stored
     * encoded password using the PasswordEncoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Security filter chain.
     *
     * Public: landing page, registration, login, static assets, error page.
     * Authenticated: the application's feature routes (USER and ADMIN).
     * Admin-only: /admin/** (requires ROLE_ADMIN).
     *
     * Order matters: the specific /admin/** rule is declared before the
     * broad .anyRequest().authenticated() fallback.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/register",
                                "/login",
                                "/403",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/dashboard/**",
                                "/subjects/**",
                                "/topics/**",
                                "/tasks/**",
                                "/exams/**",
                                "/study-sessions/**",
                                "/analytics/**",
                                "/calendar/**",
                                "/notifications/**",
                                "/profile/**",
                                "/settings/**"
                        ).authenticated()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        // Plain attribute handler: avoids Thymeleaf's
                        // "cannot create session after response committed"
                        // issue while keeping token validation in place.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler)
                        .failureUrl("/login?error")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                );

        return http.build();
    }
}
