package com.saasbilling.payment;

import com.saasbilling.dto.invoice.InvoiceItemRequest;
import com.saasbilling.dto.invoice.InvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.dto.payment.PaymentRequest;
import com.saasbilling.dto.payment.PaymentResponse;
import com.saasbilling.entity.*;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import com.saasbilling.service.InvoiceService;
import com.saasbilling.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessSettingsRepository businessSettingsRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InvoiceService invoiceService;
    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceRepository invoiceRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void partialThenFullPayment_movesInvoiceThroughPartiallyPaidToPaid() {
        Business business = createBusiness("Garage 1", "garage1@example.com");
        createSettings(business);
        Customer customer = createCustomer(business.getId(), "Vikram");
        Product product = createProduct(business.getId(), "Service Kit", new BigDecimal("1000.00"), BigDecimal.ZERO);

        setTenantContext(business.getId());

        InvoiceResponse invoice = invoiceService.create(new InvoiceRequest(
                customer.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(product.getId(), null, BigDecimal.ONE, null, null)), null, null, null));
        invoiceService.issue(invoice.id());

        // Partial payment of 400 against a 1000 invoice
        PaymentResponse partial = paymentService.record(new PaymentRequest(
                invoice.id(), new BigDecimal("400.00"), LocalDate.now(), PaymentMethod.CASH, "TXN1", null));
        assertThat(partial.amount()).isEqualByComparingTo("400.00");

        InvoiceResponse afterPartial = invoiceService.getById(invoice.id());
        assertThat(afterPartial.status()).isEqualTo("PARTIALLY_PAID");
        assertThat(afterPartial.amountPaid()).isEqualByComparingTo("400.00");
        assertThat(afterPartial.balanceDue()).isEqualByComparingTo("600.00");

        // Remaining 600 clears the balance
        paymentService.record(new PaymentRequest(
                invoice.id(), new BigDecimal("600.00"), LocalDate.now(), PaymentMethod.UPI, "TXN2", null));

        InvoiceResponse afterFull = invoiceService.getById(invoice.id());
        assertThat(afterFull.status()).isEqualTo("PAID");
        assertThat(afterFull.balanceDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void paymentExceedingBalanceDue_isRejected() {
        Business business = createBusiness("Garage 2", "garage2@example.com");
        createSettings(business);
        Customer customer = createCustomer(business.getId(), "Anita");
        Product product = createProduct(business.getId(), "Tyre", new BigDecimal("500.00"), BigDecimal.ZERO);

        setTenantContext(business.getId());

        InvoiceResponse invoice = invoiceService.create(new InvoiceRequest(
                customer.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(product.getId(), null, BigDecimal.ONE, null, null)), null, null, null));
        invoiceService.issue(invoice.id());

        assertThatThrownBy(() -> paymentService.record(new PaymentRequest(
                invoice.id(), new BigDecimal("999.00"), LocalDate.now(), PaymentMethod.CASH, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidingAPayment_revertsInvoiceStatusAndBalance() {
        Business business = createBusiness("Garage 3", "garage3@example.com");
        createSettings(business);
        Customer customer = createCustomer(business.getId(), "Deepak");
        Product product = createProduct(business.getId(), "Battery", new BigDecimal("2000.00"), BigDecimal.ZERO);

        setTenantContext(business.getId());

        InvoiceResponse invoice = invoiceService.create(new InvoiceRequest(
                customer.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(product.getId(), null, BigDecimal.ONE, null, null)), null, null, null));
        invoiceService.issue(invoice.id());

        PaymentResponse payment = paymentService.record(new PaymentRequest(
                invoice.id(), new BigDecimal("2000.00"), LocalDate.now(), PaymentMethod.CARD, null, null));
        assertThat(invoiceService.getById(invoice.id()).status()).isEqualTo("PAID");

        paymentService.voidPayment(payment.id(), new com.saasbilling.dto.payment.VoidPaymentRequest("Entered in error"));

        InvoiceResponse afterVoid = invoiceService.getById(invoice.id());
        assertThat(afterVoid.status()).isEqualTo("ISSUED");
        assertThat(afterVoid.amountPaid()).isEqualByComparingTo("0.00");
    }

    // -----------------------------------------------------------------
    private void setTenantContext(UUID businessId) {
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(UUID.randomUUID(), businessId, "test@example.com", "OWNER"));
    }

    private Business createBusiness(String name, String email) {
        Business business = new Business();
        business.setBusinessName(name);
        business.setOwnerName(name + " Owner");
        business.setEmail(email);
        business.setPhone("9999999999");
        business.setStatus(BusinessStatus.ACTIVE);
        return businessRepository.save(business);
    }

    private BusinessSettings createSettings(Business business) {
        BusinessSettings settings = new BusinessSettings();
        settings.setBusiness(business);
        return businessSettingsRepository.save(settings);
    }

    private Customer createCustomer(UUID businessId, String name) {
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setCustomerName(name);
        return customerRepository.save(customer);
    }

    private Product createProduct(UUID businessId, String name, BigDecimal price, BigDecimal taxRate) {
        Product product = new Product();
        product.setBusinessId(businessId);
        product.setProductName(name);
        product.setUnit("PCS");
        product.setPurchasePrice(price);
        product.setSellingPrice(price);
        product.setTaxRatePercent(taxRate);
        return productRepository.save(product);
    }
}
