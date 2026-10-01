package com.saasbilling.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalDiskFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskFileStorageService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "application/pdf");

    private final Path storageRoot;
    private final String publicBaseUrl;

    public LocalDiskFileStorageService(
            @Value("${app.storage.local-directory:./uploads}") String storageDirectory,
            @Value("${app.storage.public-base-url:/api/files}") String publicBaseUrl) {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload storage directory: " + storageRoot, e);
        }
    }

    @Override
    public StoredFile store(UUID businessId, String category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds the 10 MB upload limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType
                    + ". Allowed: PNG, JPEG, WEBP, PDF");
        }

        // Tenant-scoped subdirectory - one business's files never collide with another's.
        Path businessDir = storageRoot.resolve(sanitize(businessId.toString())).resolve(sanitize(category));
        try {
            Files.createDirectories(businessDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create storage directory", e);
        }

        // Server-generated name (never the client's) - avoids path tricks and collisions.
        String storedFileName = UUID.randomUUID() + extensionFor(contentType);
        Path target = businessDir.resolve(storedFileName);

        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to write uploaded file to {}", target, e);
            throw new IllegalStateException("Failed to store uploaded file", e);
        }

        String url = publicBaseUrl + "/" + businessId + "/" + category + "/" + storedFileName;
        return new StoredFile(url, storedFileName, file.getSize(), contentType);
    }

    /** Resolves a stored file's path for FileController, verifying it stays within storageRoot. */
    public Path resolveForRead(UUID businessId, String category, String fileName) {
        Path resolved = storageRoot.resolve(sanitize(businessId.toString()))
                .resolve(sanitize(category))
                .resolve(sanitize(fileName))
                .normalize();

        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return resolved;
    }

    private String sanitize(String segment) {
        return segment.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
