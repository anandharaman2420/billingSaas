package com.saasbilling.service;

import com.saasbilling.dto.invoice.InvoiceItemRequest;
import com.saasbilling.dto.invoice.InvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.dto.payment.PaymentRequest;
import com.saasbilling.dto.payment.PaymentResponse;
import com.saasbilling.dto.pos.*;
import com.saasbilling.entity.*;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PosService {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BillableServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessRepository businessRepository;
    private final BusinessSettingsRepository businessSettingsRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PosService(InvoiceService invoiceService,
                      PaymentService paymentService,
                      CustomerRepository customerRepository,
                      ProductRepository productRepository,
                      BillableServiceRepository serviceRepository,
                      CategoryRepository categoryRepository,
                      BusinessRepository businessRepository,
                      BusinessSettingsRepository businessSettingsRepository,
                      BranchRepository branchRepository,
                      UserRepository userRepository,
                      AuditLogService auditLogService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
        this.businessRepository = businessRepository;
        this.businessSettingsRepository = businessSettingsRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<PosCatalogItem> getCatalog(String keyword, UUID categoryId) {
        UUID businessId = TenantContext.currentBusinessId();

        Map<UUID, String> categoryNames = categoryRepository.findByBusinessIdAndType(businessId, CategoryType.PRODUCT)
                .stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        categoryRepository.findByBusinessIdAndType(businessId, CategoryType.SERVICE)
                .forEach(c -> categoryNames.putIfAbsent(c.getId(), c.getName()));

        List<PosCatalogItem> items = new ArrayList<>();

        // Products
        List<Product> products = productRepository.findByBranchIdAndStatus(
                TenantContext.currentBranchId(), ActiveStatus.ACTIVE);
        for (Product p : products) {
            if (categoryId != null && !Objects.equals(p.getCategoryId(), categoryId)) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                boolean matchName = p.getProductName() != null && p.getProductName().toLowerCase().contains(kw);
                boolean matchSku = p.getSku() != null && p.getSku().toLowerCase().contains(kw);
                if (!matchName && !matchSku) {
                    continue;
                }
            }
            items.add(new PosCatalogItem(
                    p.getId(),
                    p.getProductName(),
                    "PRODUCT",
                    p.getSku(),
                    p.getHsnCode(),
                    p.getUnit(),
                    p.getSellingPrice(),
                    p.getTaxRatePercent(),
                    p.getStockQuantity(),
                    p.getCategoryId(),
                    p.getCategoryId() != null ? categoryNames.get(p.getCategoryId()) : null
            ));
        }

        // Services
        List<BillableService> services = serviceRepository.findByBranchIdAndStatus(
                TenantContext.currentBranchId(), ActiveStatus.ACTIVE);
        for (BillableService s : services) {
            if (categoryId != null && !Objects.equals(s.getCategoryId(), categoryId)) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.toLowerCase();
                boolean matchName = s.getServiceName() != null && s.getServiceName().toLowerCase().contains(kw);
                boolean matchSac = s.getSacCode() != null && s.getSacCode().toLowerCase().contains(kw);
                if (!matchName && !matchSac) {
                    continue;
                }
            }
            items.add(new PosCatalogItem(
                    s.getId(),
                    s.getServiceName(),
                    "SERVICE",
                    null,
                    s.getSacCode(),
                    "SERVICE",
                    s.getPrice(),
                    s.getTaxRatePercent(),
                    BigDecimal.ZERO,
                    s.getCategoryId(),
                    s.getCategoryId() != null ? categoryNames.get(s.getCategoryId()) : null
            ));
        }

        items.sort(Comparator.comparing(PosCatalogItem::name, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    @Transactional
    public PosCheckoutResponse checkout(PosCheckoutRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        UUID userId = TenantContext.currentUserId();

        // 1. Resolve or create customer
        Customer customer = resolveCustomer(businessId, request);

        // 2. Build invoice items
        List<InvoiceItemRequest> invoiceItems = request.items().stream()
                .map(item -> new InvoiceItemRequest(
                        item.productId(),
                        item.serviceId(),
                        item.quantity(),
                        item.discountAmount(),
                        item.description()
                ))
                .toList();

        // 3. Create Draft Invoice
        InvoiceRequest invoiceRequest = new InvoiceRequest(
                customer.getId(),
                LocalDate.now(),
                LocalDate.now(),
                invoiceItems,
                request.additionalDiscountAmount() != null ? request.additionalDiscountAmount() : BigDecimal.ZERO,
                request.notes(),
                null
        );

        InvoiceResponse draft = invoiceService.create(invoiceRequest);

        // 4. Issue POS Bill immediately (assigns sequential BILL number e.g. BILL-2026-00001)
        InvoiceResponse issued = invoiceService.issuePos(draft.id());

        // 5. Record Payment
        BigDecimal grandTotal = issued.grandTotal();
        BigDecimal actualPaymentAmount = request.amountPaid() != null ? request.amountPaid() : grandTotal;
        if (actualPaymentAmount.compareTo(grandTotal) > 0) {
            actualPaymentAmount = grandTotal; // payments cannot exceed invoice balance
        }

        PaymentResponse paymentResponse = null;
        if (actualPaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            PaymentRequest payReq = new PaymentRequest(
                    issued.id(),
                    actualPaymentAmount,
                    LocalDate.now(),
                    request.paymentMethod(),
                    request.referenceNumber(),
                    request.notes()
            );
            paymentResponse = paymentService.record(payReq);
        }

        // 6. Reload updated invoice with payment details & PAID status
        InvoiceResponse completedInvoice = invoiceService.getById(issued.id());

        // 7. Deduct inventory stock for products sold
        for (PosItemRequest item : request.items()) {
            if (item.productId() != null) {
                productRepository.findByIdAndBusinessIdAndBranchId(
                        item.productId(), businessId, TenantContext.currentBranchId()).ifPresent(product -> {
                    BigDecimal stock = product.getStockQuantity() == null ? BigDecimal.ZERO : product.getStockQuantity();
                    product.setStockQuantity(stock.subtract(item.quantity()));
                    productRepository.save(product);
                });
            }
        }

        // 8. Calculate change to return
        BigDecimal tendered = request.amountTendered() != null ? request.amountTendered() : actualPaymentAmount;
        BigDecimal change = BigDecimal.ZERO;
        if (tendered.compareTo(actualPaymentAmount) > 0) {
            change = tendered.subtract(actualPaymentAmount);
        }

        // 9. Generate receipt
        PosReceiptResponse receipt = buildReceipt(businessId, userId, completedInvoice, customer,
                request.paymentMethod() != null ? request.paymentMethod().name() : "CASH",
                actualPaymentAmount, tendered, change, request.referenceNumber());

        auditLogService.record(businessId, userId, "POS_SALE_COMPLETED", "INVOICE", completedInvoice.id(), null,
                "POS Sale of " + grandTotal + " completed via " + request.paymentMethod());

        return new PosCheckoutResponse(completedInvoice, paymentResponse, change, receipt);
    }

    @Transactional(readOnly = true)
    public PosReceiptResponse getReceipt(UUID invoiceId) {
        UUID businessId = TenantContext.currentBusinessId();
        InvoiceResponse invoice = invoiceService.getById(invoiceId);
        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(
                        invoice.customerId(), businessId, TenantContext.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        String method = "CASH";
        String ref = null;
        var payments = paymentService.listForInvoice(invoiceId);
        if (!payments.isEmpty()) {
            method = payments.get(0).paymentMethod();
            ref = payments.get(0).referenceNumber();
        }

        return buildReceipt(businessId, TenantContext.currentUserId(), invoice, customer,
                method, invoice.amountPaid(), invoice.amountPaid(), BigDecimal.ZERO, ref);
    }

    private Customer resolveCustomer(UUID businessId, PosCheckoutRequest request) {
        if (request.customerId() != null) {
            return customerRepository.findByIdAndBusinessIdAndBranchId(
                            request.customerId(), businessId, TenantContext.currentBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        }

        String phone = request.customerPhone() != null ? request.customerPhone().trim() : null;
        if (phone != null && !phone.isBlank()) {
            Optional<Customer> existingByPhone = customerRepository.findFirstByBranchIdAndPhone(
                    TenantContext.currentBranchId(), phone);
            if (existingByPhone.isPresent()) {
                return existingByPhone.get();
            }
        }

        String name = (request.customerName() != null && !request.customerName().isBlank())
                ? request.customerName().trim()
                : "Walk-in Customer";

        if ("Walk-in Customer".equalsIgnoreCase(name) && (phone == null || phone.isBlank())) {
            Optional<Customer> walkIn = customerRepository.findFirstByBranchIdAndCustomerNameIgnoreCase(
                    TenantContext.currentBranchId(), "Walk-in Customer");
            if (walkIn.isPresent()) {
                return walkIn.get();
            }
        }

        Customer newCustomer = new Customer();
        newCustomer.setBusinessId(businessId);
        newCustomer.setBranchId(TenantContext.currentBranchId());
        newCustomer.setCustomerName(name);
        newCustomer.setPhone(phone);
        newCustomer.setStatus(ActiveStatus.ACTIVE);
        return customerRepository.save(newCustomer);
    }

    private PosReceiptResponse buildReceipt(UUID businessId,
                                            UUID userId,
                                            InvoiceResponse invoice,
                                            Customer customer,
                                            String paymentMethod,
                                            BigDecimal amountPaid,
                                            BigDecimal amountTendered,
                                            BigDecimal changeAmount,
                                            String paymentRef) {
        Business business = businessRepository.findById(businessId).orElse(null);
        BusinessSettings settings = businessSettingsRepository.findByBranchId(TenantContext.currentBranchId()).orElse(null);
        User cashier = userRepository.findByIdAndBusinessId(userId, businessId).orElse(null);
        Branch branch = branchRepository.findByIdAndBusinessIdAndStatus(
                TenantContext.currentBranchId(), businessId, "ACTIVE").orElse(null);

        String businessAddress = "";
        if (business != null) {
            List<String> addressParts = new ArrayList<>();
            if (branch != null && branch.getAddressLine() != null && !branch.getAddressLine().isBlank()) addressParts.add(branch.getAddressLine());
            if (branch != null && branch.getCity() != null && !branch.getCity().isBlank()) addressParts.add(branch.getCity());
            if (branch != null && branch.getState() != null && !branch.getState().isBlank()) addressParts.add(branch.getState());
            if (branch != null && branch.getPincode() != null && !branch.getPincode().isBlank()) addressParts.add(branch.getPincode());
            businessAddress = String.join(", ", addressParts);
        }

        List<PosReceiptResponse.Item> receiptItems = invoice.items().stream()
                .map(i -> new PosReceiptResponse.Item(
                        i.itemName(),
                        i.itemType(),
                        i.hsnSacCode(),
                        i.quantity(),
                        i.unitPrice(),
                        i.discountAmount(),
                        i.taxRatePercent(),
                        i.taxAmount(),
                        i.lineTotal()
                ))
                .toList();

        String billNumber = invoice.invoiceNumber() != null ? invoice.invoiceNumber() : "BILL-" + invoice.id().toString().substring(0, 8);
        return new PosReceiptResponse(
                invoice.id(),
                billNumber,
                billNumber,
                invoice.invoiceDate(),
                invoice.createdAt() != null ? invoice.createdAt() : OffsetDateTime.now(),
                cashier != null ? cashier.getFullName() : "Cashier",
                business != null ? business.getBusinessName() : "Store",
                branch != null ? branch.getBranchName() : null,
                businessAddress,
                branch != null ? branch.getPhone() : null,
                branch != null ? branch.getEmail() : null,
                branch != null ? branch.getGstin() : null,
                customer.getCustomerName(),
                customer.getPhone(),
                receiptItems,
                invoice.subtotal(),
                invoice.itemDiscountTotal(),
                invoice.additionalDiscountAmount(),
                invoice.taxableAmount(),
                invoice.cgstAmount(),
                invoice.sgstAmount(),
                invoice.igstAmount(),
                invoice.totalTax(),
                invoice.grandTotal(),
                paymentMethod,
                amountPaid,
                amountTendered,
                changeAmount,
                paymentRef,
                settings != null ? settings.getCurrency() : "INR",
                settings != null ? settings.getInvoiceNotes() : null,
                settings != null ? settings.getInvoiceTerms() : null
        );
    }
}
