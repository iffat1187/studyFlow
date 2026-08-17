package com.studyflow.studyflow.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Role-aware post-login redirect.
 *
 * After successful authentication the destination is decided by the
 * authenticated principal's authorities — never by username/email:
 *
 *     ROLE_ADMIN → /admin/dashboard
 *     anything else (including ROLE_USER or an unexpected role) → /dashboard
 *
 * The fallback is deliberately the user dashboard, so an unknown role can
 * never silently gain access to the admin area.
 */
@Component
public class RoleBasedAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String ADMIN_DEFAULT_URL = "/admin/dashboard";
    private static final String USER_DEFAULT_URL = "/dashboard";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                response.sendRedirect(request.getContextPath() + ADMIN_DEFAULT_URL);
                return;
            }
        }

        response.sendRedirect(request.getContextPath() + USER_DEFAULT_URL);
    }
}