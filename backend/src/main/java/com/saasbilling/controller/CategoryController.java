package com.saasbilling.controller;

import com.saasbilling.dto.common.CategoryDtos.CategoryRequest;
import com.saasbilling.dto.common.CategoryDtos.CategoryResponse;
import com.saasbilling.entity.CategoryType;
import com.saasbilling.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(@RequestParam CategoryType type) {
        return categoryService.list(type);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
