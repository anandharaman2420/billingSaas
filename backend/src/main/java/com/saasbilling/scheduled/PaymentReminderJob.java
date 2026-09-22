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

/**
 * Spec section 38: "Payment reminders". Runs after the overdue sweep
 * each day and emails the customer on two occasions per invoice's
 * lifecycle: once when it's 2 days from its due date (a gentle nudge),
 * and daily once it's actually OVERDUE.
 *
 * Known limitation: there's no "last reminded at" tracking yet, so an
 * OVERDUE invoice gets a reminder every single day the sweep runs,
 * not just once. Acceptable for now (daily overdue nudges are a
 * reasonable default for a small business), but a future pass could
 * add a `payments_reminder_last_sent_at` column to throttle this to,
 * say, every 3 days.
 */
@Component
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);
    private static final int DUE_SOON_DAYS_AHEAD = 2;

    private final BusinessRepository businessRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;

    public PaymentReminderJob(BusinessRepository businessRepository,
                               InvoiceRepository invoiceRepository,
                               CustomerRepository customerRepository,
                               EmailService emailService) {
        this.businessRepository = businessRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.scheduled.payment-reminder-cron:0 0 8 * * *}") // 8 AM daily by default
    @Transactional(readOnly = true)
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
        Customer customer = customerRepository.findByIdAndBusinessId(invoice.getCustomerId(), invoice.getBusinessId())
                .orElse(null);
        if (customer == null || customer.getEmail() == null || customer.getEmail().isBlank()) {
            return 0;
        }

        emailService.sendPaymentReminderEmail(customer.getEmail(), customer.getCustomerName(),
                invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "DRAFT",
                invoice.getBalanceDue(), overdue);
        return 1;
    }
}
