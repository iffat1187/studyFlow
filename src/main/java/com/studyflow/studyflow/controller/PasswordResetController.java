package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.ForgotPasswordDto;
import com.studyflow.studyflow.dto.ResetPasswordDto;
import com.studyflow.studyflow.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the Forgot Password / Password Reset flow.
 *
 * <p>Thin controller that delegates all logic to
 * {@link PasswordResetService}.  Handles the two public pages
 * (<code>/forgot-password</code> and <code>/reset-password</code>) and
 * never reveals whether an email exists in the system.</p>
 */
@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * Display the "Forgot password?" form.
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordDto")) {
            model.addAttribute("forgotPasswordDto", new ForgotPasswordDto(""));
        }
        return "auth/forgot-password";
    }

    /**
     * Process the "Forgot password?" form.
     *
     * <p>Always responds with the same generic success message whether
     * or not the email exists (account enumeration prevention). The actual
     * reset email is only sent when the account exists.</p>
     */
    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @Valid @ModelAttribute("forgotPasswordDto") ForgotPasswordDto forgotPasswordDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        passwordResetService.requestPasswordReset(forgotPasswordDto.email());

        redirectAttributes.addFlashAttribute("success",
                "If an account exists for that email, a password reset link has been sent.");

        return "redirect:/forgot-password";
    }

    /**
     * Display the password-reset form reached via the emailed link.
     *
     * <p>The token is validated here so invalid, expired or used tokens
     * never reach the form.</p>
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(
            @RequestParam(value = "token", required = false) String token,
            Model model
    ) {
        if (token == null || token.isBlank() || !passwordResetService.isTokenValid(token)) {
            model.addAttribute("error",
                    "This password reset link is invalid or has expired. Please request a new one.");
            return "auth/reset-password";
        }

        model.addAttribute("resetPasswordDto", new ResetPasswordDto(token, "", ""));
        return "auth/reset-password";
    }

    /**
     * Process the password-reset form.
     */
    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid @ModelAttribute("resetPasswordDto") ResetPasswordDto resetPasswordDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        String token = resetPasswordDto.token();

        // Re-validate the token on POST (defence in depth).
        if (!passwordResetService.isTokenValid(token)) {
            redirectAttributes.addFlashAttribute("error",
                    "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        // Bean Validation errors (missing/blank/short passwords).
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        if (!resetPasswordDto.newPassword().equals(resetPasswordDto.confirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "reset.error.mismatch",
                    "Passwords do not match.");
            return "auth/reset-password";
        }

        try {
            passwordResetService.resetPassword(token, resetPasswordDto.newPassword());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        redirectAttributes.addFlashAttribute("success",
                "Your password has been reset. You can now sign in with your new password.");

        return "redirect:/login";
    }
}
