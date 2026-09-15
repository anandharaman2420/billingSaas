package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.payment.PaymentRequest;
import com.saasbilling.dto.payment.PaymentResponse;
import com.saasbilling.dto.payment.VoidPaymentRequest;
import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.Payment;
import com.saasbilling.entity.PaymentMethod;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.InvoiceRepository;
import com.saasbilling.repository.PaymentRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final AuditLogService auditLogService;

    public PaymentService(PaymentRepository paymentRepository,
                           InvoiceRepository invoiceRepository,
                           InvoiceService invoiceService,
                           AuditLogService auditLogService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PaymentResponse record(PaymentRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        Invoice invoice = invoiceRepository.findByIdAndBusinessId(request.invoiceId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        assertPayable(invoice);

        BigDecimal balanceDue = invoice.getBalanceDue();
        if (request.amount().compareTo(balanceDue) > 0) {
            throw new IllegalArgumentException(
                    "Payment amount (" + request.amount() + ") exceeds the balance due (" + balanceDue + ")");
        }

        Payment payment = new Payment();
        payment.setBusinessId(businessId);
        payment.setInvoiceId(invoice.getId());
        payment.setCustomerId(invoice.getCustomerId());
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNumber(blankToNull(request.referenceNumber()));
        payment.setNotes(request.notes());
        payment.setRecordedByUserId(TenantContext.currentUserId());
        payment = paymentRepository.save(payment);

        syncInvoiceTotals(invoice.getId(), businessId);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PAYMENT_RECORDED",
                "PAYMENT", payment.getId(), null, null);

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse voidPayment(UUID id, VoidPaymentRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        Payment payment = paymentRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.isVoided()) {
            throw new ForbiddenOperationException("Payment is already voided");
        }

        payment.setVoided(true);
        payment.setVoidedAt(OffsetDateTime.now());
        payment.setVoidedReason(request.reason());
        payment = paymentRepository.save(payment);

        syncInvoiceTotals(payment.getInvoiceId(), businessId);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PAYMENT_VOIDED",
                "PAYMENT", payment.getId(), null, request.reason());

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID id) {
        Payment payment = paymentRepository.findByIdAndBusinessId(id, TenantContext.currentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForInvoice(UUID invoiceId) {
        UUID businessId = TenantContext.currentBusinessId();
        // Confirms tenant ownership of the invoice before returning any
        // payments against it - the payment query itself isn't
        // business-scoped, so this check is what makes the call safe.
        invoiceRepository.findByIdAndBusinessId(invoiceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        return paymentRepository.findByInvoiceIdAndVoidedFalseOrderByPaymentDateAsc(invoiceId)
                .stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(UUID invoiceId, UUID customerId, PaymentMethod method,
                                                 LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Payment> page = paymentRepository.search(
                TenantContext.currentBusinessId(), invoiceId, customerId, method, fromDate, toDate, false, pageable);
        return PageResponse.from(page.map(PaymentResponse::from));
    }

    // -----------------------------------------------------------------
    private void assertPayable(Invoice invoice) {
        switch (invoice.getStatus()) {
            case DRAFT -> throw new ForbiddenOperationException(
                    "Cannot record a payment against a draft invoice - issue it first");
            case CANCELLED -> throw new ForbiddenOperationException(
                    "Cannot record a payment against a cancelled invoice");
            case PAID -> throw new ForbiddenOperationException("Invoice is already fully paid");
            case ISSUED, PARTIALLY_PAID, OVERDUE -> { /* payable */ }
        }
    }

    private void syncInvoiceTotals(UUID invoiceId, UUID businessId) {
        BigDecimal newAmountPaid = paymentRepository.sumActiveAmountByInvoiceId(invoiceId);
        invoiceService.applyAmountPaid(invoiceId, businessId, newAmountPaid);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
