package com.saasbilling.scheduled;

import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.Customer;
import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.InvoiceStatus;
import com.saasbilling.notification.EmailService;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Spec section 38: "Payment reminders". Runs after the overdue sweep
 * each day and sends a due-soon nudge two days before an invoice's due
 * date, then throttles subsequent overdue reminders using the invoice's
 * last-reminder timestamp.
 */
@Component
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);
    private static final int DUE_SOON_DAYS_AHEAD = 2;
    private final BusinessRepository businessRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;
    private final int throttleDays;

    public PaymentReminderJob(BusinessRepository businessRepository,
                               InvoiceRepository invoiceRepository,
                               CustomerRepository customerRepository,
                               EmailService emailService,
                               @org.springframework.beans.factory.annotation.Value("${app.scheduled.payment-reminder-throttle-days:3}") int throttleDays) {
        this.businessRepository = businessRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.emailService = emailService;
        this.throttleDays = Math.max(1, throttleDays);
    }

    @Scheduled(cron = "${app.scheduled.payment-reminder-cron:0 0 8 * * *}") // 8 AM daily by default
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (Business business : businessRepository.findAll()) {
            if (business.getStatus() != BusinessStatus.ACTIVE) {
                continue;
            }

            for (Invoice invoice : invoiceRepository.findDueOnDate(business.getId(), today.plusDays(DUE_SOON_DAYS_AHEAD))) {
                sent += remind(invoice, false);
            }

            for (Invoice invoice : invoiceRepository.findByBusinessIdAndStatus(business.getId(), InvoiceStatus.OVERDUE)) {
                sent += remind(invoice, true);
            }
        }

        if (sent > 0) {
            log.info("Payment reminder job: sent {} reminder email(s)", sent);
        }
    }

    private int remind(Invoice invoice, boolean overdue) {
        OffsetDateTime now = OffsetDateTime.now();
        if (invoice.getLastReminderSentAt() != null
                && invoice.getLastReminderSentAt().plusDays(throttleDays).isAfter(now)) {
            return 0;
        }
        Customer customer = customerRepository.findByIdAndBusinessIdAndBranchId(
                        invoice.getCustomerId(), invoice.getBusinessId(), invoice.getBranchId())
                .orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return 0;
        }

        emailService.sendPaymentReminderEmail(customer.getEmail(), customer.getCustomerName(),
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "DRAFT",
                invoice.getBalanceDue(), overdue);
        invoice.setLastReminderSentAt(now);
        invoiceRepository.save(invoice);
        return 1;
    }
}
