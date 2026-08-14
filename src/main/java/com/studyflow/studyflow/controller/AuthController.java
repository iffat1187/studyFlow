package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.RegisterDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller responsible for User registration.
 *
 * Handles the public registration page and the registration
 * submission. Login, logout and route protection belong to
 * later authentication phases.
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor Injection.
     */
    public AuthController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Display the registration page.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerDto", new RegisterDto("", "", "", ""));
        return "auth/register";
    }

    /**
     * Process the registration form.
     *
     * Validates input, checks duplicate email and password match,
     * then encodes the password and saves the user with Role.USER.
     */
    @PostMapping("/register")
    public String registerUser(
            @Valid @ModelAttribute("registerDto") RegisterDto registerDto,
            BindingResult bindingResult
    ) {

        // Bean Validation errors (empty fields, invalid email, short password...).
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        // Duplicate email check.
        if (userService.existsByEmail(registerDto.email())) {
            bindingResult.rejectValue(
                    "email",
                    "register.error.duplicate",
                    "An account with this email already exists."
            );
            return "auth/register";
        }

        // Password confirmation check.
        if (!registerDto.password().equals(registerDto.confirmPassword())) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "register.error.mismatch",
                    "Passwords do not match."
            );
            return "auth/register";
        }

        // Encode the password BEFORE saving. Never store raw passwords.
        String encodedPassword = passwordEncoder.encode(registerDto.password());

        // Backend assigns Role.USER. The form has no role field.
        User user = new User(
                null,
                registerDto.name(),
                registerDto.email(),
                encodedPassword,
                Role.USER
        );

        userService.saveUser(user);

        // Public destination for now; the login page is implemented later.
        return "redirect:/login";
    }

}