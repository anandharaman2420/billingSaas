package com.saasbilling.controller;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.product.ProductRequest;
import com.saasbilling.dto.product.ProductResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Role policy: everyone can view products (needed to build an invoice).
 * Only OWNER/ADMIN/MANAGER can create, edit, or deactivate a product -
 * STAFF should not be able to change prices or stock levels.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "productName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return productService.search(keyword, status, categoryId, buildPageable(page, size, sortBy, sortDir));
    }

    @GetMapping("/low-stock")
    public PageResponse<ProductResponse> lowStock(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return productService.lowStock(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        productService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
