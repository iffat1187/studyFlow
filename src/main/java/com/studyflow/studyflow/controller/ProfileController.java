package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.ChangePasswordDto;
import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.exception.InvalidImageException;
import com.studyflow.studyflow.exception.InvalidPasswordOperationException;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
import com.studyflow.studyflow.exception.ProfileImageStoreException;
import com.studyflow.studyflow.service.ProfileService;
import com.studyflow.studyflow.storage.ProfileImageStorage;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the authenticated user's own profile.
 *
 * The controller stays thin: it never reads a userId, hidden field, URL
 * parameter or submitted email. {@link ProfileService} resolves the owner
 * directly from the Spring Security context, so neither this controller nor
 * the browser can ever target another user's profile (IDOR prevention).
 *
 * The submitted {@link ProfileDto} is validated via {@code @Valid} +
 * {@link BindingResult}; on failure the edit form is re-rendered with the
 * submitted name preserved and the field errors shown. On success the classic
 * Post/Redirect/Get returns to the profile page with a flash toast. Because
 * the service re-validates independently (defense in depth), a rejected
 * update re-renders the edit form instead of leaking an error page.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileImageStorage imageStorage;

    /**
     * Constructor injection.
     */
    public ProfileController(ProfileService profileService, ProfileImageStorage imageStorage) {
        this.profileService = profileService;
        this.imageStorage = imageStorage;
    }

    /**
     * The authenticated user's profile page.
     *
     * @param model the Thymeleaf model
     * @return the profile view
     */
    @GetMapping
    public String viewProfile(Model model) {
        model.addAttribute("profileDto", profileService.getCurrentUserProfile());
        model.addAttribute("changePasswordDto", new ChangePasswordDto("", "", ""));
        return "profile/profile";
    }

    /**
     * The authenticated user's profile edit form, pre-populated with the
     * currently stored values.
     *
     * @param model the Thymeleaf model
     * @return the edit profile view
     */
    @GetMapping("/edit")
    public String editProfile(Model model) {
        ProfileDto profileDto = profileService.getCurrentUserProfile();
        model.addAttribute("profileDto", profileDto);
        model.addAttribute("profileEmail", profileDto.email());
        return "profile/edit-profile";
    }

    /**
     * Persist the authenticated user's profile update.
     *
     * Only the display name is ever written; email, password/hash and role are
     * never accepted from the request (the email field is read-only and is not
     * submitted by the form, and the service ignores any submitted value). On
     * a validation failure the edit form is re-rendered with the submitted
     * name preserved and the email restored to the authoritative stored value.
     *
     * @param profileDto         the submitted (bound) profile data
     * @param bindingResult      validation result for the submitted data
     * @param redirectAttributes flash attributes for the success toast
     * @param model              the Thymeleaf model (edit-form re-render)
     * @return redirect to the profile page, or the edit form on failure
     */
    @PostMapping
    public String updateProfile(
            @Valid @ModelAttribute("profileDto") ProfileDto profileDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            return editFormReRender(model);
        }

        try {
            profileService.updateCurrentUserProfile(profileDto);
        } catch (InvalidProfileDataException e) {
            bindingResult.rejectValue("name", "profile.invalidName", e.getMessage());
            return editFormReRender(model);
        }

        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        return "redirect:/profile";
    }

    /**
     * Process the change-password form submission.
     *
     * Validates the DTO via Bean Validation, then delegates to the service for
     * current-password verification, new-password confirmation, encoding and
     * persistence. The service resolves the owner from the Spring Security
     * context — no userId is accepted from the form.
     *
     * On validation failure the profile page is re-rendered with the error.
     * On business-rule failure (wrong current password, mismatch, same password)
     * the profile page is re-rendered with a descriptive error message.
     * On success a flash toast confirms the change.
     *
     * @param dto               the submitted change-password data
     * @param bindingResult     validation result
     * @param redirectAttributes flash attributes for the result toast
     * @param model              the Thymeleaf model
     * @return redirect to the profile page, or the profile view on failure
     */
    @PostMapping("/change-password")
    public String changePassword(
            @Valid @ModelAttribute("changePasswordDto") ChangePasswordDto dto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("changePasswordDto", dto);
            model.addAttribute("profileDto", profileService.getCurrentUserProfile());
            return "profile/profile";
        }

        try {
            profileService.changePassword(dto);
        } catch (InvalidPasswordOperationException e) {
            model.addAttribute("changePasswordDto", dto);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("profileDto", profileService.getCurrentUserProfile());
            return "profile/profile";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully!");
        return "redirect:/profile";
    }

    /**
     * Prepare the edit-form re-render after a rejected submission.
     *
     * The {@code profileDto} model attribute is deliberately left untouched so
     * the Spring {@link BindingResult} stays attached to it (the submitted name
     * is preserved and its field errors render inline). The form never submits
     * the email field, so its authoritative value is supplied separately as
     * {@code profileEmail} — a crafted email can never be echoed back.
     *
     * @param model the Thymeleaf model
     * @return the edit profile view
     */
    private String editFormReRender(Model model) {
        model.addAttribute("profileEmail", profileService.getCurrentUserProfile().email());
        return "profile/edit-profile";
    }

    /**
     * Validate and upload a new profile picture for the current user.
     *
     * Owner-resolution and storage live in {@link ProfileService}; no
     * userId or file path is ever accepted from the request.
     *
     * @param file              the uploaded image ({@code file} form field)
     * @param redirectAttributes flash attributes for the result toast
     * @return redirect to the profile page
     */
    @PostMapping("/picture")
    public String uploadPicture(
            @RequestParam(value = "file", required = false) MultipartFile file,
            RedirectAttributes redirectAttributes) {

        try {
            profileService.uploadProfileImage(file);
            redirectAttributes.addFlashAttribute("successMessage", "Profile picture updated successfully!");
        } catch (InvalidImageException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ProfileImageStoreException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    /**
     * Remove the current user's profile picture.
     *
     * @param redirectAttributes flash attributes for the result toast
     * @return redirect to the profile page
     */
    @PostMapping("/picture/delete")
    public String deletePicture(RedirectAttributes redirectAttributes) {
        profileService.removeProfileImage();
        redirectAttributes.addFlashAttribute("successMessage", "Profile picture removed successfully!");
        return "redirect:/profile";
    }

    /**
     * Serve the current user's stored profile picture.
     *
     * Owner-scoped: only the file the authenticated user actually owns (their
     * exact {@code profileImagePath}) is served — the requested name must
     * equal the owner's stored name, so a manipulated URL can never fetch
     * another user's picture or any other file. The storage layer further
     * restricts serving to its strict safe-name pattern (no traversal).
     *
     * @param filename the requested safe file name
     * @return the image bytes, or 404 when the name is not the owner's current
     *         picture, does not decode to a safe name, or the file is gone
     */
    @GetMapping("/picture/{filename}")
    public ResponseEntity<byte[]> servePicture(@PathVariable String filename) {
        String ownedName = profileService.getCurrentUserProfileImageName();
        if (ownedName == null || !ownedName.equals(filename)) {
            return ResponseEntity.notFound().build();
        }

        var maybeImage = imageStorage.read(filename);
        if (maybeImage.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ProfileImageStorage.StoredImage image = maybeImage.get();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}