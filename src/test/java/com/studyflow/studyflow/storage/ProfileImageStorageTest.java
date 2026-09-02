package com.studyflow.studyflow.storage;

import com.studyflow.studyflow.exception.InvalidImageException;
import com.studyflow.studyflow.exception.ProfileImageStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProfileImageStorage} against a throw-away temp
 * directory ({@code @TempDir}) — no Spring context, no real upload location.
 *
 * Covers the Sub-Phase D content-validation contract:
 *  - JPEG/PNG content is accepted and stored under a generated UUID name,
 *  - non-image content (text renamed to .jpg, GIF, SVG) is rejected even with
 *    a "correct" browser MIME,
 *  - a disallowed MIME declaration is rejected even with valid image bytes,
 *  - empty, null and oversized files are rejected,
 *  - the original (possibly malicious) filename never influences the path,
 *  - traversal / unsafe requested names can never leave the root directory.
 */
class ProfileImageStorageTest {

    private static final Pattern SAFE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png)$");

    private static final String ONE_BY_ONE_GIF =
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    @TempDir
    Path tempDir;

    private ProfileImageStorage storage(int maxSizeMb) {
        return new ProfileImageStorage(tempDir.toString(), maxSizeMb);
    }

    private ProfileImageStorage storage() {
        return storage(5);
    }

    // ---------- helpers (real decodable content) ----------

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] gifBytes() {
        return Base64.getDecoder().decode(ONE_BY_ONE_GIF);
    }

    // ---------- acceptance ----------

    @Test
    void store_validPng_storesUnderGeneratedNameAndReturnsIt() throws IOException {
        byte[] content = pngBytes(8, 8);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png",
                "image/png", content);

        String name = storage().store(file);

        assertThat(name).matches(SAFE_NAME.pattern()).endsWith(".png");
        Path stored = tempDir.resolve(name).toAbsolutePath().normalize();
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void store_validJpegStoredWithJpgExtensionRegardlessOfOriginalName() throws IOException {
        byte[] content = jpegBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.PNG",
                "image/jpeg", content);

        String name = storage().store(file);

        // The extension follows the DECODED content, never the original name.
        assertThat(name).endsWith(".jpg");
        assertThat(tempDir.resolve(name)).exists();
    }

    @Test
    void store_nullContentTypeIsAcceptedAndValidatedByDecode() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png",
                null, pngBytes(4, 4));

        assertThat(storage().store(file)).matches(SAFE_NAME.pattern());
    }

    @Test
    void store_maliciousOriginalFilename_neverInfluencesStoredPath() throws IOException {
        byte[] content = pngBytes(4, 4);
        MockMultipartFile file = new MockMultipartFile("file", "..\\..\\..\\evil.png",
                "image/png", content);

        String name = storage().store(file);

        assertThat(name).matches(SAFE_NAME.pattern());
        assertThat(tempDir.resolve(name)).exists();
    }

    // ---------- rejection ----------

    @Test
    void store_textFileRenamedToJpg_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg",
                "image/jpeg", "plain text — not an image".getBytes());

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void store_gifContent_rejectedEvenWithJpegMime() {
        MockMultipartFile file = new MockMultipartFile("file", "anim.jpg",
                "image/jpeg", gifBytes());

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("JPG, JPEG and PNG");
    }

    @Test
    void store_svgContent_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "vector.svg",
                "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes());

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void store_validPngWithDisallowedMime_rejected() throws IOException {
        // Even valid image bytes are rejected when the browser MIME claims a
        // non-image type — MIME is a signal that must not contradict content.
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png",
                "text/html", pngBytes(4, 4));

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("JPG, JPEG and PNG");
    }

    @Test
    void store_oversizedFile_rejectedBeforeContentRead() throws IOException {
        // max-size-mb=1 -> any file over 1 MB is rejected.
        MockMultipartFile file = new MockMultipartFile("file", "big.png",
                "image/png", new byte[1024 * 1024 + 1]);

        assertThatThrownBy(() -> storage(1).store(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("1 MB or smaller");
    }

    @Test
    void store_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png",
                "image/png", new byte[0]);

        assertThatThrownBy(() -> storage().store(file))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void store_nullFile_rejected() {
        assertThatThrownBy(() -> storage().store(null))
                .isInstanceOf(InvalidImageException.class);
    }

    // ---------- read / delete ----------

    @Test
    void read_storedImages_returnBytesAndCorrectContentType() throws IOException {
        byte[] png = pngBytes(8, 8);
        byte[] jpeg = jpegBytes();
        ProfileImageStorage storage = storage();

        String pngName = storage.store(new MockMultipartFile("file", "c.png", "image/png", png));
        String jpegName = storage.store(new MockMultipartFile("file", "d.jpg", "image/jpeg", jpeg));

        ProfileImageStorage.StoredImage readPng = storage.read(pngName).orElseThrow();
        assertThat(readPng.bytes()).isEqualTo(png);
        assertThat(readPng.contentType()).isEqualTo("image/png");

        ProfileImageStorage.StoredImage readJpeg = storage.read(jpegName).orElseThrow();
        assertThat(readJpeg.bytes()).isEqualTo(jpeg);
        assertThat(readJpeg.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void read_absentOrUnsafeName_returnsEmpty() throws IOException {
        ProfileImageStorage storage = storage();
        assertThat(storage.read("00000000-0000-0000-0000-000000000000.png")).isEmpty();
        assertThat(storage.read("../somefile.png")).isEmpty();
        assertThat(storage.read("..\\somefile.png")).isEmpty();
        assertThat(storage.read("/etc/passwd")).isEmpty();
        assertThat(storage.read("C:\\Windows\\System32\\config\\SAM")).isEmpty();
        assertThat(storage.read("")).isEmpty();
        assertThat(storage.read(null)).isEmpty();
    }

    @Test
    void delete_storedFile_removesIt() throws IOException {
        String name = storage().store(new MockMultipartFile(
                "file", "del.png", "image/png", pngBytes(4, 4)));
        Path stored = tempDir.resolve(name);

        assertThat(stored).exists();
        assertThat(storage().delete(name)).isTrue();
        assertThat(stored).doesNotExist();
    }

    @Test
    void delete_unsafeNameNeverTouchesFilesystem_returnsFalse() throws IOException {
        Path outside = Files.createTempFile("outside", ".png");
        try {
            ProfileImageStorage storage = storage();
            assertThat(storage.delete("../" + outside.getFileName())).isFalse();
            assertThat(outside).exists(); // untouched
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void profileImageStoreException_neverUsedForInvalidInput() {
        // Invalid content produces InvalidImageException — the
        // ProfileImageStoreException type is reserved for IO failures only.
        ProfileImageStorage storage = storage();
        try {
            storage.store(new MockMultipartFile("file", "x.jpg",
                    "image/jpeg", "nope".getBytes()));
        } catch (InvalidImageException expected) {
            assertThat((Object) expected).isNotInstanceOf(ProfileImageStoreException.class);
        }
    }
}