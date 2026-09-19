package com.saasbilling.controller;

import com.saasbilling.dto.business.BusinessProfileDtos.BusinessProfileRequest;
import com.saasbilling.dto.business.BusinessProfileDtos.BusinessProfileResponse;
import com.saasbilling.service.BusinessProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business/profile")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    @GetMapping
    public BusinessProfileResponse get() {
        return businessProfileService.get();
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PutMapping
    public BusinessProfileResponse update(@Valid @RequestBody BusinessProfileRequest request) {
        return businessProfileService.update(request);
    }
}
