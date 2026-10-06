package com.rebeautifier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CoverStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final Path root;

    public CoverStorageService(@Value("${rebeautifier.cover-storage-path}") String coverStoragePath) throws IOException {
        this.root = Path.of(coverStoragePath).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public static boolean isAllowedMime(String contentType) {
        return contentType != null && ALLOWED_MIME.contains(contentType.toLowerCase());
    }

    public StoredCover store(UUID bookId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover must be 5MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_MIME.contains(contentType)) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover must be JPEG, PNG, WebP, or GIF");
        }
        try {
            return storeBytes(bookId, contentType, file.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store cover", ex);
        }
    }

    public StoredCover storeBytes(UUID bookId, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover file is required");
        }
        if (bytes.length > MAX_BYTES) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover must be 5MB or smaller");
        }
        String mime = contentType == null ? "" : contentType.toLowerCase();
        if (!ALLOWED_MIME.contains(mime)) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover must be JPEG, PNG, WebP, or GIF");
        }
        String extension = switch (mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        String key = bookId + "." + extension;
        Path target = root.resolve(key);
        try {
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store cover", ex);
        }
        return new StoredCover(key, mime);
    }

    public Optional<byte[]> readBytes(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return Optional.empty();
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public void deleteIfPresent(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(storageKey));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    public record StoredCover(String storageKey, String mimeType) {
    }
}
