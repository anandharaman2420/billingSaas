package com.saasbilling.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Abstraction over "where uploaded files live". The only implementation
 * right now is {@link LocalDiskFileStorageService} (files on the
 * container's own disk, served back through {@code /api/files/**}).
 *
 * Local disk doesn't survive a container redeploy and isn't shared
 * across replicas - for a real multi-instance production deployment,
 * swap in an S3-compatible implementation (AWS S3, MinIO, Cloudflare
 * R2) behind this same interface without touching any caller.
 */
public interface FileStorageService {

    /** Stores the file scoped by business id + logical category and returns a URL the frontend can use directly. */
    StoredFile store(UUID businessId, String category, MultipartFile file);

    record StoredFile(String url, String storedFileName, long sizeBytes, String contentType) {
    }
}
