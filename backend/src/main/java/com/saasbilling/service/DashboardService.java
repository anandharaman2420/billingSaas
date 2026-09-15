package com.saasbilling.service;

import com.saasbilling.dto.dashboard.DashboardResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.repository.InvoiceRepository;
import com.saasbilling.repository.ProductRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Every figure here comes from a live query against the tenant's own
 * data - spec section 7 explicitly forbids hardcoded/fake dashboard
 * numbers. "Sales" means billed amount on non-draft, non-cancelled
 * invoices; a DRAFT invoice hasn't been sent to the customer yet, so
 * it doesn't count as a sale.
 */
@Service
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public DashboardService(InvoiceRepository invoiceRepository,
                             CustomerRepository customerRepository,
                             ProductRepository productRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getSummary() {
        UUID businessId = TenantContext.currentBusinessId();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate allTimeStart = LocalDate.of(2000, 1, 1);

        return new DashboardResponse(
                invoiceRepository.sumSalesInRange(businessId, today, today),
                invoiceRepository.sumSalesInRange(businessId, monthStart, monthEnd),
                invoiceRepository.sumSalesInRange(businessId, allTimeStart, today),
                invoiceRepository.sumPendingBalance(businessId),
                invoiceRepository.sumPaidAmount(businessId),
                invoiceRepository.countIssuedInvoices(businessId),
                customerRepository.countByBusinessIdAndStatus(businessId, ActiveStatus.ACTIVE),
                productRepository.countByBusinessIdAndStatus(businessId, ActiveStatus.ACTIVE)
        );
    }
}
