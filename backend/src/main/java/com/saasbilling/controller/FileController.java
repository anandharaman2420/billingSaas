package com.saasbilling.controller;

import com.saasbilling.security.TenantContext;
import com.saasbilling.storage.LocalDiskFileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Serves files stored by LocalDiskFileStorageService. The URL carries a
 * business id segment, but authorization never trusts it - the read is
 * gated by the caller's own TenantContext, so an authenticated user
 * from another tenant gets a 403 even with a valid file URL.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final LocalDiskFileStorageService fileStorageService;

    public FileController(LocalDiskFileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{businessId}/{category}/{fileName}")
    public ResponseEntity<FileSystemResource> get(@PathVariable UUID businessId,
                                                    @PathVariable String category,
                                                    @PathVariable String fileName) {
        if (!businessId.equals(TenantContext.currentBusinessId())) {
            return ResponseEntity.status(403).build();
        }

        Path path = fileStorageService.resolveForRead(businessId, category, fileName);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentTypeFor(fileName)))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new FileSystemResource(path));
    }

    private String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
