package com.saasbilling.controller;

import com.saasbilling.dto.business.BusinessSettingsDtos.BusinessSettingsRequest;
import com.saasbilling.dto.business.BusinessSettingsDtos.BusinessSettingsResponse;
import com.saasbilling.service.BusinessSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business/settings")
public class BusinessSettingsController {

    private final BusinessSettingsService businessSettingsService;

    public BusinessSettingsController(BusinessSettingsService businessSettingsService) {
        this.businessSettingsService = businessSettingsService;
    }

    @GetMapping
    public BusinessSettingsResponse get() {
        return businessSettingsService.get();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PutMapping
    public BusinessSettingsResponse update(@Valid @RequestBody BusinessSettingsRequest request) {
        return businessSettingsService.update(request);
    }
}
