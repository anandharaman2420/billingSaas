package com.saasbilling.controller;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.payment.PaymentRequest;
import com.saasbilling.dto.payment.PaymentResponse;
import com.saasbilling.dto.payment.VoidPaymentRequest;
import com.saasbilling.entity.PaymentMethod;
import com.saasbilling.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Role policy: all roles (including STAFF) can record payments - it's
 * listed explicitly as a STAFF permission in spec section 5. Voiding a
 * payment is financially sensitive and restricted to OWNER/ADMIN/MANAGER.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<PaymentResponse> search(
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "paymentDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return paymentService.search(invoiceId, customerId, method, fromDate, toDate,
                buildPageable(page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentService.getById(id);
    }

    @GetMapping("/by-invoice/{invoiceId}")
    public List<PaymentResponse> listForInvoice(@PathVariable UUID invoiceId) {
        return paymentService.listForInvoice(invoiceId);
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> record(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.record(request));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/void")
    public PaymentResponse voidPayment(@PathVariable UUID id, @Valid @RequestBody VoidPaymentRequest request) {
        return paymentService.voidPayment(id, request);
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
