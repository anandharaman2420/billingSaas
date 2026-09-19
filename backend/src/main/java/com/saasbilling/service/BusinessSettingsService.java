package com.saasbilling.service;

import com.saasbilling.dto.business.BusinessSettingsDtos.BusinessSettingsRequest;
import com.saasbilling.dto.business.BusinessSettingsDtos.BusinessSettingsResponse;
import com.saasbilling.entity.BusinessSettings;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BusinessSettingsRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessSettingsService {

    private final BusinessSettingsRepository businessSettingsRepository;
    private final AuditLogService auditLogService;

    public BusinessSettingsService(BusinessSettingsRepository businessSettingsRepository, AuditLogService auditLogService) {
        this.businessSettingsRepository = businessSettingsRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public BusinessSettingsResponse get() {
        return BusinessSettingsResponse.from(getOwnSettings());
    }

    @Transactional
    public BusinessSettingsResponse update(BusinessSettingsRequest request) {
        BusinessSettings settings = getOwnSettings();

        // Lowering the sequence below what's already been issued would let
        // a future invoice reuse a number that's already on a real invoice
        // - the partial unique index on (business_id, invoice_number)
        // would reject it at insert time, but we'd rather fail fast here
        // with a clear message than have "issue invoice" mysteriously 500.
        if (request.invoiceNextSequence() < settings.getInvoiceNextSequence()) {
            throw new ForbiddenOperationException(
                    "Next invoice sequence cannot be lowered below " + settings.getInvoiceNextSequence()
                            + " - doing so risks colliding with an already-issued invoice number");
        }

        settings.setInvoicePrefix(request.invoicePrefix());
        settings.setInvoiceNumberFormat(request.invoiceNumberFormat());
        settings.setInvoiceNextSequence(request.invoiceNextSequence());
        settings.setFinancialYearStartMonth(request.financialYearStartMonth());
        settings.setDefaultDueDays(request.defaultDueDays());
        settings.setCurrency(request.currency());
        settings.setTaxMode(request.taxMode());
        settings.setInvoiceNotes(request.invoiceNotes());
        settings.setInvoiceTerms(request.invoiceTerms());
        settings.setThemePreference(request.themePreference());

        settings = businessSettingsRepository.save(settings);

        auditLogService.record(TenantContext.currentBusinessId(), TenantContext.currentUserId(),
                "SETTINGS_CHANGED", "BUSINESS_SETTINGS", settings.getId(), null, null);

        return BusinessSettingsResponse.from(settings);
    }

    private BusinessSettings getOwnSettings() {
        return businessSettingsRepository.findByBusinessId(TenantContext.currentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business settings not found"));
    }
}
