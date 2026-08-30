package com.saasbilling.controller;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.customer.CustomerRequest;
import com.saasbilling.dto.customer.CustomerResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.service.CustomerService;
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
 * Role policy (spec section 5):
 *  - OWNER/ADMIN/MANAGER/STAFF can all view and create/edit customers
 *    (STAFF needs this to bill a new walk-in customer).
 *  - Only OWNER/ADMIN/MANAGER can deactivate a customer record.
 * Enforced here with @PreAuthorize - never only by hiding a frontend button.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<CustomerResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ActiveStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "customerName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        return customerService.search(keyword, status, pageable);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return customerService.getById(id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        customerService.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100); // cap page size, never trust an unbounded client value
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
