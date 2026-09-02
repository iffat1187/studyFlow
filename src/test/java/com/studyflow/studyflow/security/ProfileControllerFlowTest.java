package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
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
 * Sub-Phase C controller-flow tests for the Profile module.
 *
 * The {@link ProfileService} (which resolves the owner from the Spring
 * Security context and is already covered by its own unit + integration
 * tests) is mocked so these tests exercise the web layer: route protection,
 * delegation, post/redirect/get + flash toast, validation re-render with
 * preserved values, and the invariant that a crafted email is never echoed
 * back from a failed submission.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerFlowTest {

    private static final String EMAIL_A = "a@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private ProfileDto profileA() {
        return new ProfileDto("User A", EMAIL_A);
    }

    @Test
    void getProfile_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getEditProfile_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void postProfile_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/profile")
                        .param("name", "User A")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(profileService, never()).updateCurrentUserProfile(any());
    }

    @Test
    void getProfile_authenticated_rendersCurrentUsersData() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(get("/profile")
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profileDto", profileA()))
                .andExpect(content().string(containsString("My Profile")))
                .andExpect(content().string(containsString("User A")))
                .andExpect(content().string(containsString("a@example.com")))
                .andExpect(content().string(containsString("Account Information")))
                .andExpect(content().string(containsString("Edit Profile")))
                .andExpect(content().string(containsString("href=\"/profile/edit\"")));

        // The controller delegates identity resolution to the security-aware service.
        verify(profileService).getCurrentUserProfile();
    }

    @Test
    void getProfile_asSecondUser_rendersOnlyTheirData() throws Exception {
        when(profileService.getCurrentUserProfile())
                .thenReturn(new ProfileDto("User B", "b@example.com"));

        mockMvc.perform(get("/profile")
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User B")))
                .andExpect(content().string(containsString("b@example.com")))
                .andExpect(content().string(not(containsString("User A"))));
    }

    @Test
    void getEditProfile_authenticated_prepopulatesForm() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(get("/profile/edit")
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profileDto", profileA()))
                .andExpect(content().string(containsString("Edit Profile")))
                .andExpect(content().string(containsString("Save Changes")))
                .andExpect(content().string(containsString("value=\"User A\"")))
                .andExpect(content().string(containsString("a@example.com")))
                .andExpect(content().string(containsString("cannot be changed")));
    }

    @Test
    void postProfile_validData_updatesAndRedirectsWithFlash() throws Exception {
        when(profileService.updateCurrentUserProfile(any(ProfileDto.class)))
                .thenReturn(new ProfileDto("User A Updated", EMAIL_A));

        mockMvc.perform(post("/profile")
                        .param("name", "User A Updated")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage", "Profile updated successfully!"));

        ArgumentCaptor<ProfileDto> captor = ArgumentCaptor.forClass(ProfileDto.class);
        verify(profileService).updateCurrentUserProfile(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("User A Updated");
    }

    @Test
    void postProfile_blankName_staysOnFormWithoutSaving() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        mockMvc.perform(post("/profile")
                        .param("name", "")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("profileDto", "name"))
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Edit Profile")))
                .andExpect(content().string(containsString("Save Changes")));

        verify(profileService, never()).updateCurrentUserProfile(any());
    }

    @Test
    void postProfile_nameTooLong_staysOnFormWithoutSaving() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());
        String tooLong = "x".repeat(101);

        mockMvc.perform(post("/profile")
                        .param("name", tooLong)
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("profileDto", "name"))
                .andExpect(content().string(containsString("at most 100 characters")));

        verify(profileService, never()).updateCurrentUserProfile(any());
    }

    @Test
    void postProfile_validationFailure_neverEchoesCraftedEmail() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());

        // A crafted request sends a bogus email alongside an invalid name.
        mockMvc.perform(post("/profile")
                        .param("name", "")
                        .param("email", "evil@attacker.com")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("a@example.com")))
                .andExpect(content().string(not(containsString("evil@attacker.com"))));

        verify(profileService, never()).updateCurrentUserProfile(any());
        verify(profileService).getCurrentUserProfile();
    }

    @Test
    void postProfile_extraRoleAndPasswordParams_areIgnored() throws Exception {
        when(profileService.updateCurrentUserProfile(any(ProfileDto.class)))
                .thenReturn(profileA());

        mockMvc.perform(post("/profile")
                        .param("name", "User A")
                        .param("email", "evil@attacker.com")
                        .param("role", "ADMIN")
                        .param("password", "hacked123")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        ArgumentCaptor<ProfileDto> captor = ArgumentCaptor.forClass(ProfileDto.class);
        verify(profileService).updateCurrentUserProfile(captor.capture());
        ProfileDto submitted = captor.getValue();

        // The DTO carries exactly the two profile fields; role/password have
        // no bindable target. The service layer never writes email/role/hash
        // regardless of what reaches it (covered by the service tests).
        assertThat(submitted.name()).isEqualTo("User A");
        assertThat(submitted.email()).isEqualTo("evil@attacker.com");
    }

    @Test
    void postProfile_serviceRejects_rerendersEditForm() throws Exception {
        when(profileService.getCurrentUserProfile()).thenReturn(profileA());
        when(profileService.updateCurrentUserProfile(any(ProfileDto.class)))
                .thenThrow(new InvalidProfileDataException("Name is required"));

        // The web-layer validation passes, so the service re-validation must
        // be the one rejecting; the edit form is re-rendered, never a 500.
        mockMvc.perform(post("/profile")
                        .param("name", "Valid Submitted Name")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("profileDto", "name"))
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Edit Profile")))
                .andExpect(content().string(containsString("Valid Submitted Name")));
    }

    @Test
    void profileRoutes_areNotCarriedOnPublicLandingPage() throws Exception {
        // Profile navigation must not leak onto the anonymous landing page.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/profile/edit"))));
    }
}