package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.invoice.CancelInvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceItemRequest;
import com.saasbilling.dto.invoice.InvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.entity.*;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Owns all invoice total calculation. Per spec section 11: "Ensure
 * calculations are done securely on the backend. Do not trust totals
 * sent from frontend." The client only ever sends WHAT to bill
 * (product/service id + quantity + optional line discount); every
 * price, tax rate, and total in the response is computed here from
 * the tenant's own Product/Service/BusinessSettings records.
 */
@Service
public class InvoiceService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BillableServiceRepository serviceRepository;
    private final BusinessSettingsRepository businessSettingsRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final AuditLogService auditLogService;
    private final com.saasbilling.pdf.InvoicePdfService invoicePdfService;
    private final com.saasbilling.notification.EmailService emailService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           CustomerRepository customerRepository,
                           ProductRepository productRepository,
                           BillableServiceRepository serviceRepository,
                           BusinessSettingsRepository businessSettingsRepository,
                           BusinessRepository businessRepository,
                           BranchRepository branchRepository,
                           AuditLogService auditLogService,
                           com.saasbilling.pdf.InvoicePdfService invoicePdfService,
                           com.saasbilling.notification.EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.serviceRepository = serviceRepository;
        this.businessSettingsRepository = businessSettingsRepository;
        this.businessRepository = businessRepository;
        this.branchRepository = branchRepository;
        this.auditLogService = auditLogService;
        this.invoicePdfService = invoicePdfService;
        this.emailService = emailService;
    }

    // -----------------------------------------------------------------
    // Create (always starts as DRAFT - no invoice number yet)
    // -----------------------------------------------------------------
    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        UUID branchId = TenantContext.currentBranchId();
        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(request.customerId(), businessId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        Invoice invoice = new Invoice();
        invoice.setBusinessId(businessId);
        invoice.setBranchId(branchId);
        invoice.setCustomerId(customer.getId());
        invoice.setStatus(InvoiceStatus.DRAFT);
        applyMutableFields(invoice, request, businessId);

        recalculateTotals(invoice, request.items(), customer, businessId);

        invoice = invoiceRepository.save(invoice);

        auditLogService.record(businessId, TenantContext.currentUserId(), "INVOICE_CREATED",
                "INVOICE", invoice.getId(), null, null);

        return InvoiceResponse.from(invoice);
    }

    // -----------------------------------------------------------------
    // Update - only permitted while still a DRAFT. Once issued, the
    // financial record is fixed; corrections happen via cancellation
    // (spec section 14: never silently rewrite an issued invoice).
    // -----------------------------------------------------------------
    @Transactional
    public InvoiceResponse update(UUID id, InvoiceRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        Invoice invoice = getOwnedWithItemsOrThrow(id, businessId);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ForbiddenOperationException("Only draft invoices can be edited. Cancel and recreate instead.");
        }

        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(
                        request.customerId(), businessId, TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        invoice.setCustomerId(customer.getId());
        applyMutableFields(invoice, request, businessId);

        recalculateTotals(invoice, request.items(), customer, businessId);

        invoice = invoiceRepository.save(invoice);

        auditLogService.record(businessId, TenantContext.currentUserId(), "INVOICE_UPDATED",
                "INVOICE", invoice.getId(), null, null);

        return InvoiceResponse.from(invoice);
    }

    // -----------------------------------------------------------------
    // Issue: DRAFT -> ISSUED. This is where the invoice number is
    // assigned, atomically and safely under concurrent requests.
    // -----------------------------------------------------------------
    @Transactional
    public InvoiceResponse issue(UUID id) {
        return issueInternal(id, InvoiceType.STANDARD);
    }

    @Transactional
    public InvoiceResponse issuePos(UUID id) {
        return issueInternal(id, InvoiceType.POS);
    }

    private InvoiceResponse issueInternal(UUID id, InvoiceType type) {
        UUID businessId = TenantContext.currentBusinessId();
        Invoice invoice = getOwnedWithItemsOrThrow(id, businessId);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ForbiddenOperationException("Only draft invoices can be issued");
        }
        if (invoice.getItems().isEmpty()) {
            throw new ForbiddenOperationException("Cannot issue an invoice with no items");
        }

        assertWithinMonthlyInvoiceLimit(businessId);

        invoice.setInvoiceType(type);
        if (type == InvoiceType.POS) {
            invoice.setInvoiceNumber(generateNextPosBillNumber(businessId));
        } else {
            invoice.setInvoiceNumber(generateNextInvoiceNumber(businessId));
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice = invoiceRepository.save(invoice);

        auditLogService.record(businessId, TenantContext.currentUserId(),
                type == InvoiceType.POS ? "POS_BILL_ISSUED" : "INVOICE_ISSUED",
                "INVOICE", invoice.getId(), null, null);

        return InvoiceResponse.from(invoice);
    }

    // -----------------------------------------------------------------
    // Cancel: never a hard delete (spec section 42). Works on any
    // non-cancelled status; the invoice and its number remain in the
    // history, just flagged CANCELLED.
    // -----------------------------------------------------------------
    @Transactional
    public InvoiceResponse cancel(UUID id, CancelInvoiceRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        Invoice invoice = getOwnedWithItemsOrThrow(id, businessId);

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new ForbiddenOperationException("Invoice is already cancelled");
        }
        if (invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new ForbiddenOperationException(
                    "Cannot cancel an invoice that has payments recorded against it. Refund/reverse the payments first.");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(java.time.OffsetDateTime.now());
        invoice.setCancellationReason(request.reason());
        invoice = invoiceRepository.save(invoice);

        auditLogService.record(businessId, TenantContext.currentUserId(), "INVOICE_CANCELLED",
                "INVOICE", invoice.getId(), null, request.reason());

        return InvoiceResponse.from(invoice);
    }

    /** Spec section 38: "Email invoices" - generates the PDF and emails it to the customer on file. */
    @Transactional(readOnly = true)
    public void emailInvoice(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Invoice invoice = getOwnedWithItemsOrThrow(id, businessId);

        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new ForbiddenOperationException("Issue the invoice before emailing it to the customer");
        }

        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(
                        invoice.getCustomerId(), businessId, invoice.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new IllegalArgumentException("This customer has no email address on file");
        }

        byte[] pdf = invoicePdfService.generate(invoice);
        emailService.sendInvoiceEmail(customer.getEmail(), customer.getCustomerName(),
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "DRAFT",
                invoice.getGrandTotal(), pdf);

        auditLogService.record(businessId, TenantContext.currentUserId(), "INVOICE_EMAILED",
                "INVOICE", invoice.getId(), null, null);
    }

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID id) {
        return InvoiceResponse.from(getOwnedWithItemsOrThrow(id, TenantContext.currentBusinessId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse.Summary> search(String keyword, InvoiceStatus status, UUID customerId,
                                                          LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Invoice> page = invoiceRepository.searchByBranch(
                TenantContext.currentBusinessId(), TenantContext.currentBranchId(), keyword, status,
                customerId, fromDate, toDate, pageable);
        return PageResponse.from(page.map(InvoiceResponse.Summary::from));
    }

    /** Used by the PDF controller, which needs the full entity, not just the DTO. */
    @Transactional(readOnly = true)
    public Invoice getEntityById(UUID id) {
        return getOwnedWithItemsOrThrow(id, TenantContext.currentBusinessId());
    }

    // -----------------------------------------------------------------
    // Invoice and POS Bill numbering - concurrency-safe via a pessimistic
    // row lock on business_settings.
    // -----------------------------------------------------------------
    private String generateNextInvoiceNumber(UUID businessId) {
        BusinessSettings settings = businessSettingsRepository.findByBranchIdForUpdate(TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Business settings not found"));

        long sequence = settings.getInvoiceNextSequence();
        String format = settings.getInvoiceNumberFormat();
        String prefix = settings.getInvoicePrefix();

        String number = formatNumber(format, prefix, sequence);

        settings.setInvoiceNextSequence(sequence + 1);
        businessSettingsRepository.save(settings);

        return number;
    }

    private String generateNextPosBillNumber(UUID businessId) {
        BusinessSettings settings = businessSettingsRepository.findByBranchIdForUpdate(TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Business settings not found"));

        long sequence = settings.getPosNextSequence();
        String format = settings.getPosNumberFormat();
        String prefix = settings.getPosPrefix();

        String number = formatNumber(format, prefix, sequence);

        settings.setPosNextSequence(sequence + 1);
        businessSettingsRepository.save(settings);

        return number;
    }

    /**
     * Supports placeholders:
     * - {PREFIX} -> replaced with invoicePrefix or posPrefix (e.g. INV, BILL)
     * - {YEAR} or YYYY -> current 4-digit calendar year (e.g. 2026)
     * - {SEQ:00000} or {SEQ} or 00001 -> zero-padded sequence (e.g. 00001, 00003)
     */
    private String formatNumber(String format, String prefix, long sequence) {
        if (format == null || format.isBlank()) {
            format = "{PREFIX}-{YEAR}-{SEQ:00000}";
        }
        if (prefix == null || prefix.isBlank()) {
            prefix = "INV";
        }

        String year = String.valueOf(LocalDate.now().getYear());

        String result = format
                .replace("{PREFIX}", prefix)
                .replace("{YEAR}", year)
                .replace("YYYY", year);

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{SEQ:(0+)}").matcher(result);
        if (matcher.find()) {
            int width = matcher.group(1).length();
            String padded = String.format("%0" + width + "d", sequence);
            result = matcher.replaceAll(padded);
        } else if (result.contains("{SEQ}")) {
            String padded = String.format("%05d", sequence);
            result = result.replace("{SEQ}", padded);
        } else {
            java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("(0{3,}[0-9]*)").matcher(result);
            if (numMatcher.find()) {
                int width = numMatcher.group(1).length();
                String padded = String.format("%0" + width + "d", sequence);
                result = numMatcher.replaceFirst(padded);
            } else {
                result = result + "-" + String.format("%05d", sequence);
            }
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Totals calculation
    // -----------------------------------------------------------------
    private void recalculateTotals(Invoice invoice, java.util.List<InvoiceItemRequest> itemRequests,
                                    Customer customer, UUID businessId) {
        invoice.getItems().clear();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        int sortOrder = 0;
        for (InvoiceItemRequest itemRequest : itemRequests) {
            InvoiceItem item = buildItem(itemRequest, businessId, sortOrder++);
            invoice.addItem(item);

            subtotal = subtotal.add(item.getUnitPrice().multiply(item.getQuantity()));
            itemDiscountTotal = itemDiscountTotal.add(item.getDiscountAmount());
            taxTotal = taxTotal.add(item.getTaxAmount());
        }

        subtotal = subtotal.setScale(MONEY_SCALE, ROUNDING);
        itemDiscountTotal = itemDiscountTotal.setScale(MONEY_SCALE, ROUNDING);
        taxTotal = taxTotal.setScale(MONEY_SCALE, ROUNDING);

        BigDecimal taxableAmount = subtotal.subtract(itemDiscountTotal).setScale(MONEY_SCALE, ROUNDING);

        // GST split: CGST+SGST for an intra-state sale (business and
        // customer in the same state), IGST for inter-state - or when
        // either party's state isn't on file, in which case we can't
        // determine intra vs inter-state, so it's shown as IGST pending
        // manual review. This is a simplification for MVP; a business
        // handling both intra- and inter-state sales at scale should
        // have an accountant verify GST treatment, per the disclaimer
        // in spec section 12 ("do not claim legal/tax compliance").
        boolean sameState = isSameState(businessId, customer);
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        if (sameState) {
            cgst = taxTotal.divide(BigDecimal.valueOf(2), MONEY_SCALE, ROUNDING);
            sgst = taxTotal.subtract(cgst);
        } else {
            igst = taxTotal;
        }

        BigDecimal additionalDiscount = invoice.getAdditionalDiscountAmount() == null
                ? BigDecimal.ZERO : invoice.getAdditionalDiscountAmount().setScale(MONEY_SCALE, ROUNDING);

        BigDecimal grandTotal = taxableAmount.add(taxTotal).subtract(additionalDiscount).setScale(MONEY_SCALE, ROUNDING);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO; // an oversized additional discount never produces a negative invoice
        }

        invoice.setSubtotal(subtotal);
        invoice.setItemDiscountTotal(itemDiscountTotal);
        invoice.setAdditionalDiscountAmount(additionalDiscount);
        invoice.setTaxableAmount(taxableAmount);
        invoice.setCgstAmount(cgst);
        invoice.setSgstAmount(sgst);
        invoice.setIgstAmount(igst);
        invoice.setTotalTax(taxTotal);
        invoice.setGrandTotal(grandTotal);
    }

    private InvoiceItem buildItem(InvoiceItemRequest request, UUID businessId, int sortOrder) {
        boolean hasProduct = request.productId() != null;
        boolean hasService = request.serviceId() != null;
        if (hasProduct == hasService) {
            throw new IllegalArgumentException("Each item must reference exactly one product or service");
        }

        InvoiceItem item = new InvoiceItem();
        item.setSortOrder(sortOrder);
        item.setQuantity(request.quantity());
        item.setDescription(request.description());

        BigDecimal unitPrice;
        BigDecimal taxRate;

        if (hasProduct) {
            Product product = productRepository.findByIdAndBusinessIdAndBranchId(
                            request.productId(), businessId, TenantContext.currentBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            if (product.getStatus() != ActiveStatus.ACTIVE) {
                throw new ForbiddenOperationException("Product '" + product.getProductName() + "' is not active");
            }
            item.setItemType(InvoiceItemType.PRODUCT);
            item.setProductId(product.getId());
            item.setItemName(product.getProductName());
            item.setHsnSacCode(product.getHsnCode());
            unitPrice = product.getSellingPrice();
            taxRate = product.getTaxRatePercent();
        } else {
            BillableService service = serviceRepository.findByIdAndBusinessIdAndBranchId(
                            request.serviceId(), businessId, TenantContext.currentBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
            if (service.getStatus() != ActiveStatus.ACTIVE) {
                throw new ForbiddenOperationException("Service '" + service.getServiceName() + "' is not active");
            }
            item.setItemType(InvoiceItemType.SERVICE);
            item.setServiceId(service.getId());
            item.setItemName(service.getServiceName());
            item.setHsnSacCode(service.getSacCode());
            unitPrice = service.getPrice();
            taxRate = service.getTaxRatePercent();
        }

        item.setUnitPrice(unitPrice);
        item.setTaxRatePercent(taxRate);

        BigDecimal lineSubtotal = unitPrice.multiply(item.getQuantity());
        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        if (discount.compareTo(lineSubtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed the line amount for '" + item.getItemName() + "'");
        }
        item.setDiscountAmount(discount.setScale(MONEY_SCALE, ROUNDING));

        BigDecimal lineTaxableAmount = lineSubtotal.subtract(discount);
        BigDecimal taxAmount = lineTaxableAmount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, ROUNDING);
        item.setTaxAmount(taxAmount);

        item.setLineTotal(lineTaxableAmount.add(taxAmount).setScale(MONEY_SCALE, ROUNDING));

        return item;
    }

    private boolean isSameState(UUID businessId, Customer customer) {
        Branch branch = branchRepository.findByIdAndBusinessIdAndStatus(
                TenantContext.currentBranchId(), businessId, "ACTIVE").orElse(null);
        if (branch == null || branch.getState() == null || customer.getState() == null) {
            return false;
        }
        return branch.getState().trim().equalsIgnoreCase(customer.getState().trim());
    }

    /** Spec section 37: plan limits are checked at issue time, since a DRAFT hasn't consumed anything yet. */
    private void assertWithinMonthlyInvoiceLimit(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        long issuedThisMonth = invoiceRepository.countIssuedInvoicesInRange(businessId, monthStart, monthEnd);

        if (!PlanLimits.forPlan(business.getPlan()).isWithinMonthlyInvoiceLimit(issuedThisMonth)) {
            throw new ForbiddenOperationException(
                    "This business's " + business.getPlan() + " plan allows a limited number of invoices per month. Upgrade your plan to issue more.");
        }
    }

    private void applyMutableFields(Invoice invoice, InvoiceRequest request, UUID businessId) {
        invoice.setInvoiceDate(request.invoiceDate());

        LocalDate dueDate = request.dueDate();
        if (dueDate == null) {
            int defaultDueDays = businessSettingsRepository.findByBranchId(TenantContext.currentBranchId())
                    .map(BusinessSettings::getDefaultDueDays).orElse(7);
            dueDate = request.invoiceDate().plusDays(defaultDueDays);
        }
        invoice.setDueDate(dueDate);
        invoice.setAdditionalDiscountAmount(request.additionalDiscountAmount());
        invoice.setNotes(request.notes());
        invoice.setTerms(request.terms());
    }

    /**
     * Called by PaymentService after every payment is recorded or
     * voided. Recomputes amount_paid from the authoritative sum of
     * non-voided payments (never incrementally adjusted, so it can't
     * drift) and rolls the status forward/back accordingly. Does not
     * touch a CANCELLED invoice's status.
     */
    @Transactional
    public void applyAmountPaid(UUID invoiceId, UUID businessId, BigDecimal amountPaid) {
        Invoice invoice = invoiceRepository.findByIdAndBusinessIdAndBranchId(
                        invoiceId, businessId, TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        invoice.setAmountPaid(amountPaid);

        if (invoice.getStatus() != InvoiceStatus.CANCELLED) {
            boolean fullyPaid = invoice.getGrandTotal().compareTo(BigDecimal.ZERO) > 0
                    && amountPaid.compareTo(invoice.getGrandTotal()) >= 0;

            if (fullyPaid) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            } else if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID) {
                // Every payment against this invoice was voided - revert to ISSUED.
                // If it's also past due, the OVERDUE sweep (InvoiceRepository#findOverdueCandidates)
                // will pick it back up; we don't duplicate that date logic here.
                invoice.setStatus(InvoiceStatus.ISSUED);
            }
        }

        invoiceRepository.save(invoice);
    }

    // -----------------------------------------------------------------
    private Invoice getOwnedWithItemsOrThrow(UUID id, UUID businessId) {
        return invoiceRepository.findWithItemsByIdAndBusinessIdAndBranchId(id, businessId, TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }
}
