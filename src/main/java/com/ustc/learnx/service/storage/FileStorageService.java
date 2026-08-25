package com.ustc.learnx.service.storage;

import com.ustc.learnx.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files on disk and streams them back.
 *
 * <p>Files used to be held as {@code @Lob byte[]} on the resource row, so every
 * library listing pulled every file's bytes into the heap. They now live under
 * {@code learnx.storage.root} — a mounted volume in a container deployment —
 * and the database keeps only a key.
 *
 * <p>Keys are generated here and never taken from the client, so a caller
 * cannot steer reads or writes outside the storage root.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final DateTimeFormatter KEY_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * Extensions accepted for study material. Anything executable by a browser
     * or a shell is refused, since these files are served back to users.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "csv", "rtf", "odt", "odp", "ods",
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp",
            "zip", "rar", "7z");

    private final Path root;
    private final long maxBytes;

    public FileStorageService(
            @Value("${learnx.storage.root:${java.io.tmpdir}/learnx-files}") String storageRoot,
            @Value("${learnx.storage.max-file-size-bytes:52428800}") long maxBytes) {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    /** What was written, so the caller can record it on the owning row. */
    public record StoredFile(String storageKey, long size, String sha256) {
    }

    /**
     * Streams an upload to disk without buffering it in memory.
     *
     * @return the key to persist, plus the size and digest of what was written
     */
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("No file was supplied");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException(
                    "File is larger than the " + (maxBytes / (1024 * 1024)) + " MB limit");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidUploadException("Files of type '" + extension + "' are not accepted");
        }

        String key = KEY_PREFIX.format(LocalDate.now()) + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);

        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written;
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(target);
                 DigestOutputStream digesting = new DigestOutputStream(out, digest)) {
                written = in.transferTo(digesting);
            }
            if (written > maxBytes) {
                Files.deleteIfExists(target);
                throw new InvalidUploadException("File exceeded the allowed size while uploading");
            }
            return new StoredFile(key, written, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        } catch (IOException e) {
            throw new StorageException("Could not store the uploaded file", e);
        }
    }

    /** Stores raw bytes, used for images decoded from a data URL. */
    public StoredFile store(byte[] content, String extension) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("No content was supplied");
        }
        if (content.length > maxBytes) {
            throw new InvalidUploadException("Content is larger than the allowed limit");
        }
        String safeExtension = ALLOWED_EXTENSIONS.contains(extension) ? extension : "bin";
        String key = KEY_PREFIX.format(LocalDate.now()) + "/" + UUID.randomUUID() + "." + safeExtension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new StoredFile(key, content.length, HexFormat.of().formatHex(digest.digest(content)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        } catch (IOException e) {
            throw new StorageException("Could not store the content", e);
        }
    }

    /**
     * The stored file as a streamable resource. Returning this type lets Spring
     * MVC copy it straight to the response and honour range requests, so a
     * large download never lands in the heap.
     */
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isReadable(path)) {
            throw new NotFoundException("The stored file is missing");
        }
        return new FileSystemResource(path);
    }

    public boolean exists(String storageKey) {
        return storageKey != null && Files.isReadable(resolve(storageKey));
    }

    /** Removes a stored file. Missing files are not an error. */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // The owning row is already gone; a leftover file is not worth failing on.
            log.warn("Could not delete stored file {}: {}", storageKey, e.getMessage());
        }
    }

    /**
     * Maps a key to a path inside the storage root.
     *
     * <p>The normalised result is checked against the root, so a key containing
     * {@code ..} cannot escape it even if one is ever persisted.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new NotFoundException("No file is attached");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Rejected a storage key that escapes the storage root", null);
        }
        return resolved;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** The upload was refused. Mapped to HTTP 400. */
    public static class InvalidUploadException extends RuntimeException {
        public InvalidUploadException(String message) {
            super(message);
        }
    }

    /** The store itself failed. Mapped to HTTP 500. */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
