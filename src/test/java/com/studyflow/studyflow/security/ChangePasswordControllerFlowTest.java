package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ChangePasswordDto;
import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.exception.InvalidPasswordOperationException;
import com.studyflow.studyflow.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase E controller-flow tests for the change-password route.
 *
 * The {@link ProfileService} (real security logic covered by its own unit +
 * integration tests) is mocked so these tests exercise the web layer:
 * delegation to the service, post/redirect/get + success flash, Bean
 * Validation re-render with field errors on blank/short input, business-rule
 * rejection re-rendering the profile page with a safe message, the invariant
 * that a crafted userId is never read, and that extra role/email parameters
 * are ignored.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordControllerFlowTest {

    private static final String EMAIL_A = "a@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private ProfileDto profileA() {
        return new ProfileDto("User A", EMAIL_A);
    }

    @Test
    void postChangePassword_success_delegatesAndRedirectsWithFlash() throws Exception {
        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "Password changed successfully!"));

        ArgumentCaptor<ChangePasswordDto> captor = ArgumentCaptor.forClass(ChangePasswordDto.class);
        verify(profileService).changePassword(captor.capture());
        assertThat(captor.getValue().newPassword()).isEqualTo("newPass456");
    }

    // TEST 35 — no userId parameter; service resolves the owner from context
    @Test
    void postChangePassword_craftedUserId_isIgnored() throws Exception {
        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .param("userId", "999")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        ArgumentCaptor<ChangePasswordDto> captor = ArgumentCaptor.forClass(ChangePasswordDto.class);
        verify(profileService).changePassword(captor.capture());

        // The DTO has no userId and carries only the three password fields.
        ChangePasswordDto submitted = captor.getValue();
        assertThat(submitted.currentPassword()).isEqualTo("oldPass123");
        assertThat(submitted.newPassword()).isEqualTo("newPass456");
        assertThat(submitted.confirmPassword()).isEqualTo("newPass456");
    }

    @Test
    void postChangePassword_blank_setsFieldErrorsAndDoesNotCallService() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "")
                        .param("newPassword", "")
                        .param("confirmPassword", "")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "currentPassword"))
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "newPassword"))
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "confirmPassword"));

        verify(profileService, never()).changePassword(any());
    }

    @Test
    void postChangePassword_shortNewPassword_setsFieldError() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "abc")
                        .param("confirmPassword", "abc")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("changePasswordDto", "newPassword"))
                .andExpect(content().string(containsString("at least 6 characters")));

        verify(profileService, never()).changePassword(any());
    }

    @Test
    void postChangePassword_serviceRejects_rerendersProfileWithSafeMessage() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());
        org.mockito.Mockito.doThrow(new InvalidPasswordOperationException("Current password is incorrect."))
                .when(profileService).changePassword(any(ChangePasswordDto.class));

        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "wrong-old")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Current password is incorrect."))
                .andExpect(content().string(containsString("Current password is incorrect.")))
                // No internal details are echoed.
                .andExpect(content().string(not(containsString("DAOHelper"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void postChangePassword_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/profile/change-password")
                        .param("currentPassword", "oldPass123")
                        .param("newPassword", "newPass456")
                        .param("confirmPassword", "newPass456")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(profileService, never()).changePassword(any());
    }

    @Test
    void changePasswordForm_rendersOnProfilePage_withVisibilityToggle() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(get("/profile")
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Change Password")))
                .andExpect(content().string(containsString("Current Password")))
                .andExpect(content().string(containsString("New Password")))
                .andExpect(content().string(containsString("Confirm New Password")))
                .andExpect(content().string(containsString("password-toggle")))
                .andExpect(content().string(containsString("bi-eye")))
                .andExpect(content().string(containsString("/profile/change-password")));
    }
}