package com.saasbilling.controller;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.service.ServiceRequest;
import com.saasbilling.dto.service.ServiceResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.service.BillableServiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final BillableServiceService serviceService;

    public ServiceController(BillableServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public PageResponse<ServiceResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "serviceName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return serviceService.search(keyword, status, categoryId, buildPageable(page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ServiceResponse getById(@PathVariable UUID id) {
        return serviceService.getById(id);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceService.create(request));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return serviceService.update(id, request);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        serviceService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        serviceService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
