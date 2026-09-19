package com.saasbilling.dto.business;

import com.saasbilling.entity.BusinessSettings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class BusinessSettingsDtos {

    public record BusinessSettingsRequest(
            @NotBlank(message = "Invoice prefix is required")
            String invoicePrefix,

            @NotBlank(message = "Invoice number format is required")
            @Pattern(regexp = ".*\\{SEQ:0+}.*", message = "Format must include a {SEQ:00000}-style placeholder")
            String invoiceNumberFormat,

            @Min(value = 1, message = "Next invoice sequence must be at least 1")
            long invoiceNextSequence,

            @Min(value = 1) @jakarta.validation.constraints.Max(value = 12)
            short financialYearStartMonth,

            @Min(value = 0, message = "Default due days cannot be negative")
            int defaultDueDays,

            @NotBlank
            String currency,

            @Pattern(regexp = "EXCLUSIVE|INCLUSIVE", message = "Tax mode must be EXCLUSIVE or INCLUSIVE")
            String taxMode,

            String invoiceNotes,
            String invoiceTerms,

            @Pattern(regexp = "LIGHT|DARK|SYSTEM", message = "Theme must be LIGHT, DARK, or SYSTEM")
            String themePreference
    ) {
    }

    public record BusinessSettingsResponse(
            String invoicePrefix, String invoiceNumberFormat, long invoiceNextSequence,
            short financialYearStartMonth, int defaultDueDays, String currency, String taxMode,
            String invoiceNotes, String invoiceTerms, String themePreference
    ) {
        public static BusinessSettingsResponse from(BusinessSettings s) {
            return new BusinessSettingsResponse(
                    s.getInvoicePrefix(), s.getInvoiceNumberFormat(), s.getInvoiceNextSequence(),
                    s.getFinancialYearStartMonth(), s.getDefaultDueDays(), s.getCurrency(), s.getTaxMode(),
                    s.getInvoiceNotes(), s.getInvoiceTerms(), s.getThemePreference());
        }
    }
}
