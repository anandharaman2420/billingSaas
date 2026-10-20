package com.saasbilling.scheduled;

import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.Customer;
import com.saasbilling.entity.Invoice;
import com.saasbilling.notification.EmailService;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReminderJobTest {

    @Mock private BusinessRepository businessRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private EmailService emailService;

    @Test
    void repeatedRunsThrottleRemindersForThreeDays() {
        PaymentReminderJob job = new PaymentReminderJob(businessRepository, invoiceRepository,
                customerRepository, emailService, 3);
        Business business = new Business();
        business.setId(UUID.randomUUID());
        business.setStatus(BusinessStatus.ACTIVE);

        UUID branchId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setBusinessId(business.getId());
        invoice.setBranchId(branchId);
        invoice.setCustomerId(UUID.randomUUID());
        invoice.setInvoiceNumber("INV-1");
        invoice.setGrandTotal(new BigDecimal("100.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);

        Customer customer = new Customer();
        customer.setEmail("customer@example.com");
        customer.setCustomerName("Customer");

        when(businessRepository.findAll()).thenReturn(List.of(business));
        when(invoiceRepository.findDueOnDate(eq(business.getId()), any(LocalDate.class))).thenReturn(List.of(invoice));
        when(invoiceRepository.findByBusinessIdAndStatus(any(), any())).thenReturn(List.of());
        when(customerRepository.findByIdAndBusinessIdAndBranchId(invoice.getCustomerId(), business.getId(), branchId))
                .thenReturn(Optional.of(customer));

        job.run();
        job.run();

        verify(emailService, times(1)).sendPaymentReminderEmail(
                "customer@example.com", "Customer", "INV-1", new BigDecimal("100.00"), false);
        verify(invoiceRepository, times(1)).save(invoice);
    }
}
