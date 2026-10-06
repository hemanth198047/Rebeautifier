package com.rebeautifier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class CoverStorageService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final Path root;

    public CoverStorageService(@Value("${rebeautifier.cover-storage-path}") String coverStoragePath) throws IOException {
        this.root = Path.of(coverStoragePath).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public StoredCover store(UUID bookId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_MIME.contains(contentType)) {
            throw com.rebeautifier.web.error.ApiException.badRequest("Cover must be JPEG, PNG, WebP, or GIF");
        }
        String extension = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        String key = bookId + "." + extension;
        Path target = root.resolve(key);
        try {
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store cover", ex);
        }
        return new StoredCover(key, contentType);
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
