package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.service.ProfileService;
import com.studyflow.studyflow.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
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
 * Sub-Phase F controller-flow tests for the Settings routes.
 *
 * The {@link UserSettingsService} (real logic covered by its own unit +
 * integration tests) is mocked so these tests exercise the web layer:
 * delegation to the service, post/redirect/get + success flash, Bean
 * Validation re-render with field errors on invalid input, the invariant
 * that a crafted userId is never read, and that a binding failure re-renders
 * the form instead of 500ing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerFlowTest {

    private static final String EMAIL_A = "settings-flow-a@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSettingsService userSettingsService;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void getSettings_rendersCurrentUserSettings() throws Exception {
        when(userSettingsService.getCurrentUserSettings())
                .thenReturn(UserSettingsDto.defaults());

        mockMvc.perform(get("/settings").with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("userSettingsDto", equalTo(UserSettingsDto.defaults())))
                .andExpect(model().attribute("userSettingsDto",
                        equalTo(new UserSettingsDto(25, StudySessionType.FOCUS, 300))))
                .andExpect(content().string(containsString("Default Focus Duration")))
                .andExpect(content().string(containsString("Weekly Study Goal")));
    }

    @Test
    void postSettings_validDelegatesAndRedirectsWithFlash() throws Exception {
        UserSettingsDto submitted = new UserSettingsDto(30, StudySessionType.REVISION, 600);
        when(userSettingsService.updateCurrentUserSettings(eq(submitted)))
                .thenReturn(submitted);

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "30")
                        .param("defaultSessionType", "REVISION")
                        .param("weeklyStudyGoalMinutes", "600")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("successMessage", "Settings saved successfully."));

        verify(userSettingsService).updateCurrentUserSettings(submitted);
    }

    @Test
    void postSettings_craftedUserId_isIgnored() throws Exception {
        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "45")
                        .param("defaultSessionType", "PRACTICE")
                        .param("weeklyStudyGoalMinutes", "900")
                        .param("userId", "999")
                        .param("email", "attacker@example.com")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));

        ArgumentCaptor<UserSettingsDto> captor = ArgumentCaptor.forClass(UserSettingsDto.class);
        verify(userSettingsService).updateCurrentUserSettings(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new UserSettingsDto(45, StudySessionType.PRACTICE, 900));
    }

    @Test
    void postSettings_bindingFailure_reRendersWithoutDelegating() throws Exception {
        when(userSettingsService.getCurrentUserSettings())
                .thenReturn(UserSettingsDto.defaults());

        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "45")
                        .param("defaultSessionType", "NOT-A-TYPE")
                        .param("weeklyStudyGoalMinutes", "900")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("userSettingsDto"));

        verify(userSettingsService, never()).updateCurrentUserSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postSettings_validationFailure_reRendersWithErrors() throws Exception {
        mockMvc.perform(post("/settings")
                        .param("defaultFocusDurationMinutes", "45")
                        .param("defaultSessionType", "PRACTICE")
                        .param("weeklyStudyGoalMinutes", "-5")
                        .with(csrf())
                        .with(user(EMAIL_A).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weekly study goal must be at least 1 minute.")));

        verify(userSettingsService, never()).updateCurrentUserSettings(org.mockito.ArgumentMatchers.any());
    }
}