package com.saasbilling.dto.pos;

import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.dto.payment.PaymentResponse;

import java.math.BigDecimal;

public record PosCheckoutResponse(
        InvoiceResponse invoice,
        PaymentResponse payment,
        BigDecimal changeAmount,
        PosReceiptResponse receipt
) {
}
