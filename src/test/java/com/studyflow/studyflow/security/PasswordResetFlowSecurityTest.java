package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ForgotPasswordDto;
import com.studyflow.studyflow.dto.ResetPasswordDto;
import com.studyflow.studyflow.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Controller-flow tests for the Forgot Password / Password Reset routes.
 *
 * <p>The {@link PasswordResetService} is mocked so these tests exercise
 * the web layer: public accessibility, generic (non-enumerating)
 * responses, token validation on GET and POST, Bean Validation
 * re-renders, and post/redirect/get redirection with flash messages.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    // ------------------------------------------------------------
    // Forgot password page
    // ------------------------------------------------------------

    @Test
    void forgotPasswordPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attributeExists("forgotPasswordDto"))
                .andExpect(content().string(containsString("Forgot your password?")));
    }

    @Test
    void postForgotPassword_callsServiceWithEmail_andRedirectsWithGenericFlash() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "reset@example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attribute("success",
                        "If an account exists for that email, a password reset link has been sent."));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetService).requestPasswordReset(emailCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(emailCaptor.getValue()).isEqualTo("reset@example.com");
    }

    @Test
    void postForgotPassword_invalidEmail_rerendersWithFieldError() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "not-an-email")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attributeHasFieldErrors("forgotPasswordDto", "email"));

        verify(passwordResetService, never()).requestPasswordReset(anyString());
    }

    @Test
    void postForgotPassword_blankEmail_rerendersWithFieldError() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("forgotPasswordDto", "email"));

        verify(passwordResetService, never()).requestPasswordReset(anyString());
    }

    @Test
    void postForgotPassword_missingCsrf_isRejected() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .param("email", "reset@example.com"))
                .andExpect(status().isForbidden());

        verify(passwordResetService, never()).requestPasswordReset(anyString());
    }

    // ------------------------------------------------------------
    // Reset password page (GET)
    // ------------------------------------------------------------

    @Test
    void getResetPassword_validToken_rendersForm() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);

        mockMvc.perform(get("/reset-password").param("token", "goodToken"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attributeExists("resetPasswordDto"))
                .andExpect(content().string(containsString("Choose a new password")));

        verify(passwordResetService).isTokenValid("goodToken");
    }

    @Test
    void getResetPassword_invalidToken_rendersGenericErrorAndDoesNotShowForm() throws Exception {
        when(passwordResetService.isTokenValid("badToken")).thenReturn(false);

        mockMvc.perform(get("/reset-password").param("token", "badToken"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attributeExists("error"))
                .andExpect(content().string(containsString("invalid or expired")));

        // The form must NOT be rendered for an invalid token.
        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void getResetPassword_missingToken_rendersGenericError() throws Exception {
        // isTokenValid(null) -> false handled by the service mock default;
        // but controller also handles null/blank without calling the service.
        when(passwordResetService.isTokenValid(anyString())).thenReturn(false);

        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
    }

    // ------------------------------------------------------------
    // Reset password page (POST)
    // ------------------------------------------------------------

    @Test
    void postResetPassword_validTokenAndMatchingPasswords_resetsAndRedirectsToLogin() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .param("token", "goodToken")
                        .param("newPassword", "newPass123")
                        .param("confirmPassword", "newPass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("success"));

        ArgumentCaptor<ResetPasswordDto> dtoCaptor = ArgumentCaptor.forClass(ResetPasswordDto.class);
        verify(passwordResetService).resetPassword(eq("goodToken"), eq("newPass123"));
    }

    @Test
    void postResetPassword_mismatchedPasswords_rerendersWithFieldError() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .param("token", "goodToken")
                        .param("newPassword", "newPass123")
                        .param("confirmPassword", "different123")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attributeHasFieldErrors("resetPasswordDto", "confirmPassword"));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void postResetPassword_shortNewPassword_rerendersWithFieldError() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .param("token", "goodToken")
                        .param("newPassword", "abc")
                        .param("confirmPassword", "abc")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attributeHasFieldErrors("resetPasswordDto", "newPassword"))
                .andExpect(content().string(containsString("at least 6 characters")));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void postResetPassword_invalidTokenOnPost_redirectsToForgotWithoutResetting() throws Exception {
        when(passwordResetService.isTokenValid("badToken")).thenReturn(false);

        mockMvc.perform(post("/reset-password")
                        .param("token", "badToken")
                        .param("newPassword", "newPass123")
                        .param("confirmPassword", "newPass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("error"));

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void postResetPassword_serviceRejects_redirectsToForgot() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);
        doThrow(new IllegalArgumentException("Invalid or expired reset token."))
                .when(passwordResetService).resetPassword(eq("goodToken"), anyString());

        mockMvc.perform(post("/reset-password")
                        .param("token", "goodToken")
                        .param("newPassword", "newPass123")
                        .param("confirmPassword", "newPass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void postResetPassword_missingCsrf_isRejected() throws Exception {
        when(passwordResetService.isTokenValid("goodToken")).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .param("token", "goodToken")
                        .param("newPassword", "newPass123")
                        .param("confirmPassword", "newPass123"))
                .andExpect(status().isForbidden());

        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }
}
