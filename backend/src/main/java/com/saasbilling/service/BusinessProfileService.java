package com.saasbilling.service;

import com.saasbilling.dto.business.BusinessProfileDtos.BusinessProfileRequest;
import com.saasbilling.dto.business.BusinessProfileDtos.BusinessProfileResponse;
import com.saasbilling.entity.Business;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final BusinessRepository businessRepository;
    private final AuditLogService auditLogService;

    public BusinessProfileService(BusinessRepository businessRepository, AuditLogService auditLogService) {
        this.businessRepository = businessRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse get() {
        return BusinessProfileResponse.from(getOwnBusiness());
    }

    @Transactional
    public BusinessProfileResponse update(BusinessProfileRequest request) {
        Business business = getOwnBusiness();

        business.setBusinessName(request.businessName());
        business.setBusinessType(request.businessType());
        business.setAddressLine(request.addressLine());
        business.setCity(request.city());
        business.setState(request.state());
        business.setPincode(request.pincode());
        if (request.country() != null && !request.country().isBlank()) {
            business.setCountry(request.country());
        }
        business.setPhone(request.phone());
        if (request.email() != null && !request.email().isBlank()) {
            business.setEmail(request.email());
        }
        business.setWebsite(request.website());
        business.setLogoUrl(request.logoUrl());
        business.setGstin(request.gstin());

        business = businessRepository.save(business);

        auditLogService.record(business.getId(), TenantContext.currentUserId(), "BUSINESS_PROFILE_UPDATED",
                "BUSINESS", business.getId(), null, null);

        return BusinessProfileResponse.from(business);
    }

    private Business getOwnBusiness() {
        // "Own business" here means the tenant the caller's JWT is scoped
        // to - there is exactly one Business per tenant, so no separate
        // ownership check is needed the way there is for Customer/Product/etc.
        return businessRepository.findById(TenantContext.currentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }
}
