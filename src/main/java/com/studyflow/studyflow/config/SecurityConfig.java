package com.studyflow.studyflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Minimal Spring Security configuration for the public registration flow.
 *
 * NOTE: This is intentionally a PARTIAL configuration.
 * It only makes the landing page, registration page and static assets
 * publicly accessible while CSRF protection stays fully enabled.
 * Full authentication (login, logout, route protection, roles) will be
 * built out in a later authentication phase.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
     * Minimal security filter chain.
     * Public: landing page, registration, login, static assets, error page.
     * Everything else remains authenticated.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/register",
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        // Plain attribute handler: avoids Thymeleaf's
                        // "cannot create session after response committed"
                        // issue while keeping token validation in place.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                )
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .logout(Customizer.withDefaults());

        return http.build();
    }
}