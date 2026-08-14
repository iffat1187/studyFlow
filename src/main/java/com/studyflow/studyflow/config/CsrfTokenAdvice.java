package com.studyflow.studyflow.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes Spring Security's CSRF token safe to use from Thymeleaf.
 *
 * Thymeleaf resolves the token lazily while rendering a form's th:action,
 * which can happen AFTER the response has been committed and fail with
 * "Cannot create a session after the response has been committed".
 *
 * This advice forces the token to be materialized (session + token value)
 * before the view renders, so template processing only reads the
 * already-generated token.
 */
@ControllerAdvice
public class CsrfTokenAdvice {

    @ModelAttribute
    public void materializeCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Force deferred token initialization now, while writing to the
            // response is still allowed.
            csrfToken.getToken();
        }
    }
}
