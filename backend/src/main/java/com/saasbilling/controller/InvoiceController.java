package com.saasbilling.controller;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.invoice.CancelInvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.InvoiceStatus;
import com.saasbilling.pdf.InvoicePdfService;
import com.saasbilling.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Role policy (spec section 5):
 *  - All roles (including STAFF) can create and view invoices, and
 *    edit while still a DRAFT - STAFF's whole job is billing customers.
 *  - Issuing and cancelling are restricted to OWNER/ADMIN/MANAGER,
 *    since issuing assigns a permanent invoice number and cancelling
 *    is a financially sensitive action.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping
    public PageResponse<InvoiceResponse.Summary> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "invoiceDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return invoiceService.search(keyword, status, customerId, fromDate, toDate,
                buildPageable(page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return invoiceService.getById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        Invoice invoice = invoiceService.getEntityById(id);
        byte[] pdf = invoicePdfService.generate(invoice);

        String filename = (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "DRAFT-" + invoice.getId())
                + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@PathVariable UUID id) {
        return invoiceService.issue(id);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelInvoiceRequest request) {
        return invoiceService.cancel(id, request);
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageRequest.of(Math.max(page, 0), safeSize, sort);
    }
}
