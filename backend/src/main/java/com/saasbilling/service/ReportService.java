package com.saasbilling.service;

import com.saasbilling.dto.report.ReportDtos.*;
import com.saasbilling.entity.Category;
import com.saasbilling.entity.Customer;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CategoryRepository categoryRepository;

    public ReportService(InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          ExpenseRepository expenseRepository,
                          CustomerRepository customerRepository,
                          InvoiceItemRepository invoiceItemRepository,
                          CategoryRepository categoryRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.expenseRepository = expenseRepository;
        this.customerRepository = customerRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public SalesReport salesReport(LocalDate fromDate, LocalDate toDate) {
        UUID businessId = TenantContext.currentBusinessId();
        var rows = invoiceRepository.dailySalesInRange(businessId, fromDate, toDate);

        var breakdown = rows.stream()
                .map(r -> new DailySalesPoint(r.getInvoiceDate(), r.getTotal(), r.getCount()))
                .toList();

        BigDecimal total = breakdown.stream().map(DailySalesPoint::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = breakdown.stream().mapToLong(DailySalesPoint::invoiceCount).sum();

        return new SalesReport(fromDate, toDate, total, count, breakdown);
    }

    @Transactional(readOnly = true)
    public InvoiceReport invoiceReport() {
        UUID businessId = TenantContext.currentBusinessId();
        var counts = invoiceRepository.countByStatus(businessId).stream()
                .map(r -> new InvoiceStatusCount(r.getStatus().name(), r.getCount()))
                .toList();
        return new InvoiceReport(counts);
    }

    @Transactional(readOnly = true)
    public PaymentReport paymentReport(LocalDate fromDate, LocalDate toDate) {
        UUID businessId = TenantContext.currentBusinessId();
        var rows = paymentRepository.sumByMethodInRange(businessId, fromDate, toDate);

        var byMethod = rows.stream()
                .map(r -> new PaymentMethodTotal(r.getPaymentMethod().name(), r.getTotal(), r.getCount()))
                .toList();

        BigDecimal total = byMethod.stream().map(PaymentMethodTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PaymentReport(fromDate, toDate, total, byMethod);
    }

    @Transactional(readOnly = true)
    public CustomerReport customerReport(LocalDate fromDate, LocalDate toDate, int limit) {
        UUID businessId = TenantContext.currentBusinessId();
        var rows = invoiceRepository.topCustomersInRange(businessId, fromDate, toDate, PageRequest.of(0, limit));

        var customerIds = rows.stream().map(InvoiceRepository.CustomerSalesRow::getCustomerId).toList();
        Map<UUID, String> nameById = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getCustomerName));

        var topCustomers = rows.stream()
                .map(r -> new TopCustomer(r.getCustomerId(), nameById.getOrDefault(r.getCustomerId(), "Unknown"),
                        r.getTotal(), r.getInvoiceCount()))
                .toList();

        BigDecimal outstanding = invoiceRepository.sumPendingBalance(businessId);

        return new CustomerReport(fromDate, toDate, topCustomers, outstanding);
    }

    @Transactional(readOnly = true)
    public ProductReport productReport(LocalDate fromDate, LocalDate toDate, int limit) {
        UUID businessId = TenantContext.currentBusinessId();
        var rows = invoiceItemRepository.topSellingProducts(businessId, fromDate, toDate, PageRequest.of(0, limit));

        var topProducts = rows.stream()
                .map(r -> new TopProduct(r.getProductId(), r.getItemName(), r.getTotalQuantity(), r.getTotalAmount()))
                .toList();

        return new ProductReport(fromDate, toDate, topProducts);
    }

    @Transactional(readOnly = true)
    public ExpenseReport expenseReport(LocalDate fromDate, LocalDate toDate) {
        UUID businessId = TenantContext.currentBusinessId();
        BigDecimal total = expenseRepository.sumInRange(businessId, fromDate, toDate);
        var rows = expenseRepository.sumByCategoryInRange(businessId, fromDate, toDate);

        var categories = categoryRepository.findByBusinessIdAndType(businessId, com.saasbilling.entity.CategoryType.EXPENSE)
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));

        var byCategory = rows.stream()
                .map(r -> new ExpenseCategoryTotal(
                        r.getCategoryId(),
                        r.getCategoryId() == null ? "Uncategorized" : categories.getOrDefault(r.getCategoryId(), "Unknown"),
                        r.getTotal()))
                .toList();

        return new ExpenseReport(fromDate, toDate, total, byCategory);
    }
}
