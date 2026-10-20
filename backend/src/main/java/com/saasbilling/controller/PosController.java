package com.saasbilling.controller;

import com.saasbilling.dto.pos.PosCatalogItem;
import com.saasbilling.dto.pos.PosCheckoutRequest;
import com.saasbilling.dto.pos.PosCheckoutResponse;
import com.saasbilling.dto.pos.PosReceiptResponse;
import com.saasbilling.service.PosService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER', 'STAFF')")
public class PosController {

    private final PosService posService;

    public PosController(PosService posService) {
        this.posService = posService;
    }

    @GetMapping("/catalog")
    public List<PosCatalogItem> getCatalog(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) UUID categoryId) {
        return posService.getCatalog(keyword, categoryId);
    }

    @PostMapping("/checkout")
    public ResponseEntity<PosCheckoutResponse> checkout(@Valid @RequestBody PosCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(posService.checkout(request));
    }

    @GetMapping("/receipt/{invoiceId}")
    public PosReceiptResponse getReceipt(@PathVariable UUID invoiceId) {
        return posService.getReceipt(invoiceId);
    }
}
