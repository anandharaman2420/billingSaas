package com.saasbilling.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidPaymentRequest(
        @NotBlank(message = "A reason is required to void a payment")
        @Size(max = 255)
        String reason
) {
}
