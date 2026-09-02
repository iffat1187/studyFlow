package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase D end-to-end web tests for the profile-picture feature against
 * the LIVE application (real beans, real DB, real filesystem under
 * {@code target/test-uploads}).
 *
 * Uploads are redirected to a throw-away directory and the 5 MB limit is
 * reduced to 1 MB here so an oversize upload can be exercised cheaply. All
 * DB writes roll back after each test ({@code @Transactional}); files on
 * disk are created/deleted by the code paths under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.upload.profile-images=target/test-uploads/profile-images",
        "app.upload.profile-images.max-size-mb=1",
        "spring.servlet.multipart.max-file-size=2MB",
        "spring.servlet.multipart.max-request-size=2MB"
})
class ProfilePictureWebIntegrationTest {

    private static final Pattern SAFE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");

    private static final String GIF_BASE64 =
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.upload.profile-images}")
    private String uploadDir;

    @AfterEach
    void tearDown() {
        // No security context is set in these tests (authentication is
        // injected per-request via MockMvc).
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "pic-" + System.nanoTime() + "@example.com";
    }

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .name(email)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    private String storedPath(String email) {
        return userRepository.findByEmail(email).orElseThrow().getProfileImagePath();
    }

    private Path onDisk(String filename) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename);
    }

    private static byte[] imageBytes(String format, int width, int height) throws IOException {
        // JPEG cannot encode alpha; use RGB for JPEG so ImageIO produces a
        // real, decodable JPEG stream (an ARGB source silently yields nothing).
        BufferedImage image = new BufferedImage(width, height,
                "jpg".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, ThreadLocalRandom.current().nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] gifBytes() {
        return Base64.getDecoder().decode(GIF_BASE64);
    }

    // ---------- security ----------

    @Test
    void upload_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void servePicture_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile/picture/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void upload_withoutCsrf_forbidden() throws Exception {
        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(user(uniqueEmail()).roles("USER")))
                .andExpect(status().isForbidden());
    }

    // ---------- happy path ----------

    @Test
    void uploadPng_thenServe_backAndProfilePageShowIt() throws Exception {
        String email = uniqueEmail();
        saveUser(email);
        byte[] png = imageBytes("png", 8, 8);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png", "image/png", png))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage",
                        "Profile picture updated successfully!"));

        String name = storedPath(email);
        assertThat(name).matches(SAFE_NAME.pattern()).endsWith(".png");
        assertThat(onDisk(name)).exists();

        mockMvc.perform(get("/profile/picture/" + name)
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png));

        mockMvc.perform(get("/profile")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/profile/picture/" + name)))
                .andExpect(content().string(containsString("class=\"profile-photo-remove\"")))
                .andExpect(content().string(containsString("Change Photo")));
    }

    @Test
    void uploadJpeg_originalNameIsPngButContentIsJpeg_storedAsJpg() throws Exception {
        String email = uniqueEmail();
        saveUser(email);
        byte[] jpeg = imageBytes("jpg", 8, 8);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "photo.png",
                                "image/jpeg", jpeg))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        "Profile picture updated successfully!"));

        String name = storedPath(email);
        // Content is authoritative: the .png original name is ignored.
        assertThat(name).endsWith(".jpg");

        mockMvc.perform(get("/profile/picture/" + name)
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpeg));
    }

    // ---------- rejection ----------

    @Test
    void upload_textFileRenamedToPng_rejectedWithFlashAndNoDbChange() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "fake.png",
                                "image/png", "plain text, not an image".getBytes()))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("errorMessage",
                        containsString("not a valid JPG or PNG")));

        assertThat(storedPath(email)).isNull();
    }

    @Test
    void upload_gifContent_rejectedEvenWithJpegMime() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "anim.jpg",
                                "image/jpeg", gifBytes()))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Only JPG, JPEG and PNG images are allowed.")));

        assertThat(storedPath(email)).isNull();
    }

    @Test
    void upload_validContentWithNonImageMime_rejected() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png",
                                "text/plain", imageBytes("png", 4, 4)))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Only JPG, JPEG and PNG images are allowed.")));

        assertThat(storedPath(email)).isNull();
    }

    @Test
    void upload_oversizedImage_rejectedWithFriendlyMessage() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        // ~1.2 MB of incompressible noise: under the 2 MB multipart limit but
        // over the 1 MB storage-side limit.
        byte[] big = imageBytes("png", 650, 650);
        assertThat(big.length).isGreaterThan(1024 * 1024);
        assertThat(big.length).isLessThan(2 * 1024 * 1024);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "big.png", "image/png", big))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("1 MB or smaller")));

        assertThat(storedPath(email)).isNull();
    }

    // ---------- replace / remove / ownership ----------

    @Test
    void upload_replace_deletesOldFileAfterNewIsSaved() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "first.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String first = storedPath(email);
        assertThat(onDisk(first)).exists();

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "second.jpg",
                                "image/jpeg", imageBytes("jpg", 4, 4)))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        "Profile picture updated successfully!"));

        String second = storedPath(email);
        assertThat(second).isNotEqualTo(first);
        assertThat(onDisk(second)).exists();
        assertThat(onDisk(first)).doesNotExist(); // old file removed after save
    }

    @Test
    void removePicture_clearsReferenceAndDeletesFile() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String name = storedPath(email);
        assertThat(onDisk(name)).exists();

        mockMvc.perform(post("/profile/picture/delete")
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("successMessage",
                        "Profile picture removed successfully!"));

        assertThat(storedPath(email)).isNull();
        assertThat(onDisk(name)).doesNotExist();
    }

    @Test
    void servePicture_otherUsersPicture_notFound() throws Exception {
        String owner = uniqueEmail();
        String other = uniqueEmail();
        saveUser(owner);
        saveUser(other);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "owner.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(user(owner).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String ownerName = storedPath(owner);

        mockMvc.perform(get("/profile/picture/" + ownerName)
                        .with(user(other).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void servePicture_missingFile_returnsNotFoundEvenWhenNameIsOwned() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(multipart("/profile/picture")
                        .file(new MockMultipartFile("file", "pic.png",
                                "image/png", imageBytes("png", 4, 4)))
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String name = storedPath(email);
        Files.deleteIfExists(onDisk(name));

        mockMvc.perform(get("/profile/picture/" + name)
                        .with(user(email).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void servePicture_traversalName_isRejected() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(get("/profile/picture/..%2F..%2F..%2Fapplication.properties")
                        .with(user(email).roles("USER")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void profilePageWithoutPicture_showsDefaultAvatarAndNoRemoveButton() throws Exception {
        String email = uniqueEmail();
        saveUser(email);

        mockMvc.perform(get("/profile")
                        .with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bi-person-circle")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("class=\"profile-photo-remove\""))))
                .andExpect(content().string(containsString("Upload Photo")));
    }
}