package com.saasbilling.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 255)
        String reason
) {
}
