package com.studyflow.studyflow.storage;

import com.studyflow.studyflow.exception.InvalidImageException;
import com.studyflow.studyflow.exception.ProfileImageStoreException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Low-level storage for validated profile pictures.
 *
 * <p><b>Security model.</b> The uploaded content is the source of truth — not
 * the original filename, not the browser-declared MIME type, not
 * client-side JS validation:
 *
 * <ol>
 *   <li>empty/oversized files are rejected up front (size checked here too,
 *       independent of the servlet-container multipart limit),</li>
 *   <li>the bytes {\em must} decode as a real JPEG or PNG via
 *       {@link javax.imageio.ImageIO} (a text file renamed to {@code .jpg}
 *       produces no reader and is rejected),</li>
 *   <li>the browser MIME type is treated as one additional signal — a
 *       non-null MIME outside {@code image/jpeg}/{@code image/png} is rejected,</li>
 *   <li>the stored name is always a generated {@link UUID} + validated
 *       extension, so the original (possibly malicious) filename can never
 *       influence the storage path,</li>
 *   <li>every path into this directory is built from a strict safe-name
 *       pattern (hex UUID + allowed extension), so traversal attempts such as
 *       {@code ../}, {@code ..\}, absolute paths or sub-directories can never
 *       point outside the profile-image directory.</li>
 * </ol>
 *
 * <p>The directory is configurable and created lazily on first use.
 */
@Component
public class ProfileImageStorage {

    private static final long MB = 1024L * 1024L;

    /**
     * Only these browser MIME declarations are accepted; any other non-null
     * MIME value is rejected. The MIME is a signal only — the authoritative
     * check is whether the bytes decode as a real image.
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png");

    /**
     * Generated names look like {@code 34f1a2b3-....-.... </code>+ suffix}.
     * Anything else (separators, {@code ..}, absolute paths) fails the
     * pattern and is rejected before touching the filesystem.
     */
    private static final Pattern SAFE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png)$",
            Pattern.CASE_INSENSITIVE);

    private final Path root;
    private final long maxBytes;
    private final long maxSizeMb;

    /**
     * Constructor injection from configuration.
     *
     * @param directory the configurable profile-image upload directory
     * @param maxSizeMb the storage-side size guard in megabytes
     */
    public ProfileImageStorage(
            @Value("${app.upload.profile-images:uploads/profile-images}") String directory,
            @Value("${app.upload.profile-images.max-size-mb:5}") long maxSizeMb) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        this.maxSizeMb = maxSizeMb;
        this.maxBytes = maxSizeMb * MB;
    }

    /**
     * Validate the submitted file and store it under a generated safe name.
     *
     * @param file the uploaded file
     * @return the generated safe file name (UUID + validated extension)
     * @throws InvalidImageException      when the content is rejected
     * @throws ProfileImageStoreException when the file cannot be saved to disk
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Choose a profile image to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidImageException(
                    "Profile image must be " + maxSizeMb + " MB or smaller.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidImageException("Only JPG, JPEG and PNG images are allowed.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ProfileImageStoreException(
                    "Could not read the uploaded profile image. Please try again.", e);
        }

        String extension = detectValidExtension(content);
        String filename = UUID.randomUUID().toString() + extension;

        Path target = resolveSafe(filename);
        try {
            Files.createDirectories(root);
            Files.write(target, content);
        } catch (IOException e) {
            throw new ProfileImageStoreException(
                    "Could not save the profile image. Please try again.", e);
        }
        return filename;
    }

    /**
     * Read a stored image by its safe name.
     *
     * @param filename the safe generated name
     * @return the image bytes and content type, or empty when the name is
     *         invalid or the file does not exist (never throws)
     */
    public Optional<StoredImage> read(String filename) {
        Path target;
        try {
            target = resolveSafe(filename);
        } catch (ProfileImageStoreException e) {
            return Optional.empty();
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredImage(
                    Files.readAllBytes(target), contentTypeFor(filename)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Delete a stored image by its safe name.
     *
     * @param filename the safe generated name
     * @return {@code true} when an existing file was removed, {@code false}
     *         when the file did not exist or deletion failed (non-fatal —
     *         callers must never invalidate a user's active picture because
     *         an old file could not be removed)
     */
    public boolean delete(String filename) {
        Path target;
        try {
            target = resolveSafe(filename);
        } catch (ProfileImageStoreException e) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Decode the bytes and confirm they are a real, fully-decodable JPEG or
     * PNG image, returning the canonical stored extension (".jpg" or ".png").
     *
     * <p>The extension is derived from the {@em decoded content} — never from
     * the original filename — so a PNG renamed to {@code evil.jpg} is stored
     * (and later served) as PNG, and a text file renamed to {@code fake.jpg}
     * is rejected because no image reader can decode it.
     *
     * @param content the uploaded bytes
     * @return ".jpg" for JPEG content, ".png" for PNG content
     * @throws InvalidImageException for undecodable or unsupported content
     */
    private String detectValidExtension(byte[] content) {
        String format;
        try (ImageInputStream in = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (in == null) {
                throw new InvalidImageException(
                        "The selected file is not a valid JPG or PNG image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidImageException(
                        "The selected file is not a valid JPG or PNG image.");
            }
            String detected = readers.next().getFormatName();
            format = detected == null ? "" : detected.toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            throw new ProfileImageStoreException(
                    "Could not read the uploaded profile image. Please try again.", e);
        }

        if ("jpeg".equals(format) || "jpg".equals(format)) {
            requireDecodable(content);
            return ".jpg";
        }
        if ("png".equals(format)) {
            requireDecodable(content);
            return ".png";
        }
        throw new InvalidImageException("Only JPG, JPEG and PNG images are allowed.");
    }

    /**
     * Full decode to reject truncated/corrupted images that a format reader
     * might still recognise from their signature bytes.
     */
    private void requireDecodable(byte[] content) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new InvalidImageException(
                    "The selected file is not a valid JPG or PNG image.");
        }
        if (image == null) {
            throw new InvalidImageException(
                    "The selected file is not a valid JPG or PNG image.");
        }
    }

    /**
     * Resolve a safe generated filename inside the profile-image directory.
     *
     * <p>The strict {@link #SAFE_NAME} pattern is the primary defense: it
     * accepts only the generated UUID-shaped names, so any traversal form
     * ({@code ../}, {@code ..\}, absolute paths, separators) is rejected
     * before a path is even built. The normalized containment check below is
     * defense-in-depth on top of the pattern.
     *
     * @param filename the safe generated name
     * @return the resolved path guaranteed to sit under {@link #root}
     */
    private Path resolveSafe(String filename) {
        if (filename == null || filename.isBlank()
                || !SAFE_NAME.matcher(filename.toLowerCase(Locale.ROOT)).matches()) {
            throw new ProfileImageStoreException("Invalid image file name.");
        }
        Path candidate = root.resolve(filename).normalize();
        if (!candidate.startsWith(root)) {
            throw new ProfileImageStoreException("Invalid image file name.");
        }
        return candidate;
    }

    /**
     * Content type for a stored safe name, derived only from its extension.
     */
    private String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    /**
     * The validated bytes of a stored image plus the content type to serve.
     */
    public record StoredImage(byte[] bytes, String contentType) {
    }
}