package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidImageException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.ProfileServiceImpl;
import com.studyflow.studyflow.storage.ProfileImageStorage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the profile-picture flows in {@link ProfileServiceImpl}.
 *
 * The impl delegates validation/storage to {@link ProfileImageStorage}
 * (mocked) and resolves the owner from the security context:
 *  - upload stores the new file first, saves the reference, THEN deletes the
 *    old file (replace order is asserted),
 *  - a storage rejection or DB failure never leaves a dangling reference or
 *    an orphaned new file,
 *  - remove clears the reference before deleting the file, and never deletes
 *    when there is nothing to remove,
 *  - the picture owner is always the AUTHENTICATED user — no ids accepted.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplImageTest {

    private static final String EMAIL = "pi-image@example.com";
    private static final String OLD_NAME = "old-00000000-0000-0000-0000-000000000001.jpg";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorage imageStorage;

    private Validator validator;

    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new ProfileServiceImpl(userRepository, validator, imageStorage,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User authenticateAs(String email, String imagePath) {
        User user = User.builder().id(1L).name(email).email(email)
                .password("hash").role(Role.USER).profileImagePath(imagePath).build();
        UserDetails details = new org.springframework.security.core.userdetails.User(
                email, "encoded", List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "encoded",
                        details.getAuthorities()));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
    }

    // ---------- upload ----------

    @Test
    void upload_storesNewFirstSavesThenDeletesOld() {
        authenticateAs(EMAIL, OLD_NAME);
        when(imageStorage.store(any())).thenReturn("new-uuid-aaaa.png");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfileDto result = service.uploadProfileImage(pngFile());

        // Order assertion: storage write happens before the old file is
        // touched; the DB reference is saved before the delete too.
        var inOrder = org.mockito.Mockito.inOrder(imageStorage, userRepository);
        inOrder.verify(imageStorage).store(any());
        inOrder.verify(userRepository).save(any());
        inOrder.verify(imageStorage).delete(OLD_NAME);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProfileImagePath()).isEqualTo("new-uuid-aaaa.png");
        assertThat(result.name()).isEqualTo(EMAIL);
        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    void upload_noPreviousImage_neverDeletesAnyFile() {
        authenticateAs(EMAIL, null);
        when(imageStorage.store(any())).thenReturn("new-uuid-aaaa.png");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadProfileImage(pngFile());

        verify(imageStorage, never()).delete(any());
    }

    @Test
    void upload_storageRejects_leavesUserAndFilesUntouched() {
        authenticateAs(EMAIL, OLD_NAME);
        when(imageStorage.store(any())).thenThrow(new InvalidImageException("Rejected."));

        assertThatThrownBy(() -> service.uploadProfileImage(pngFile()))
                .isInstanceOf(InvalidImageException.class);

        verify(userRepository, never()).save(any());
        verify(imageStorage, never()).delete(any());
    }

    @Test
    void upload_saveFails_deletesOrphanedNewFileAndOldStaysReferenced() {
        authenticateAs(EMAIL, OLD_NAME);
        when(imageStorage.store(any())).thenReturn("new-uuid-aaaa.png");
        when(userRepository.save(any())).thenThrow(new IllegalStateException("db boom"));

        assertThatThrownBy(() -> service.uploadProfileImage(pngFile()))
                .isInstanceOf(IllegalStateException.class);

        // The brand-new file must not leak as an orphan...
        verify(imageStorage).delete("new-uuid-aaaa.png");
        // ...and the OLD file must never be deleted while still referenced.
        verify(imageStorage, never()).delete(OLD_NAME);
    }

    @Test
    void upload_noAuthenticatedUser_throwsAndDoesNothing() {
        assertThatThrownBy(() -> service.uploadProfileImage(pngFile()))
                .isInstanceOf(UserNotFoundException.class);

        verify(imageStorage, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    // ---------- remove ----------

    @Test
    void remove_clearsReferenceThenDeletesFile() {
        authenticateAs(EMAIL, OLD_NAME);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfileDto result = service.removeProfileImage();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProfileImagePath()).isNull();
        verify(imageStorage).delete(OLD_NAME);
        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    void remove_noImage_neverSavesOrDeletes() {
        authenticateAs(EMAIL, null);

        ProfileDto result = service.removeProfileImage();

        verify(userRepository, never()).save(any());
        verify(imageStorage, never()).delete(any());
        assertThat(result.name()).isEqualTo(EMAIL);
    }

    @Test
    void remove_noAuthenticatedUser_throws() {
        assertThatThrownBy(() -> service.removeProfileImage())
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
        verify(imageStorage, never()).delete(any());
    }

    // ---------- current name ----------

    @Test
    void currentName_returnsStoredPathOfAuthenticatedUser() {
        authenticateAs(EMAIL, OLD_NAME);

        assertThat(service.getCurrentUserProfileImageName()).isEqualTo(OLD_NAME);
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void currentName_returnsNullWhenNoPicture() {
        authenticateAs(EMAIL, null);

        assertThat(service.getCurrentUserProfileImageName()).isNull();
    }

    @Test
    void currentName_noAuthenticatedUser_throws() {
        assertThatThrownBy(() -> service.getCurrentUserProfileImageName())
                .isInstanceOf(UserNotFoundException.class);
    }
}