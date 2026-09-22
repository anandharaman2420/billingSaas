package com.saasbilling.scheduled;

import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.InvoiceStatus;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep flipping ISSUED/PARTIALLY_PAID invoices past their due
 * date to OVERDUE. Operates directly on repositories across every
 * active business rather than through the tenant-scoped service layer
 * (which assumes a single authenticated request's TenantContext) -
 * a scheduled job spans every tenant, so it deliberately bypasses that
 * and works with explicit business ids instead.
 */
@Component
public class OverdueInvoiceSweepJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceSweepJob.class);

    private final BusinessRepository businessRepository;
    private final InvoiceRepository invoiceRepository;

    public OverdueInvoiceSweepJob(BusinessRepository businessRepository, InvoiceRepository invoiceRepository) {
        this.businessRepository = businessRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Scheduled(cron = "${app.scheduled.overdue-sweep-cron:0 0 1 * * *}") // 1 AM daily by default
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now();
        int totalFlagged = 0;

        for (Business business : businessRepository.findAll()) {
            if (business.getStatus() != BusinessStatus.ACTIVE) {
                continue;
            }
            List<Invoice> overdue = invoiceRepository.findOverdueCandidates(business.getId(), today);
            for (Invoice invoice : overdue) {
                invoice.setStatus(InvoiceStatus.OVERDUE);
            }
            if (!overdue.isEmpty()) {
                invoiceRepository.saveAll(overdue);
                totalFlagged += overdue.size();
            }
        }

        if (totalFlagged > 0) {
            log.info("Overdue invoice sweep: flagged {} invoice(s) as OVERDUE", totalFlagged);
        }
    }
}
