package com.saasbilling.invoice;

import com.saasbilling.dto.invoice.InvoiceItemRequest;
import com.saasbilling.dto.invoice.InvoiceRequest;
import com.saasbilling.dto.invoice.InvoiceResponse;
import com.saasbilling.entity.*;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvoiceServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private BusinessSettingsRepository businessSettingsRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private com.saasbilling.service.InvoiceService invoiceService;
    @Autowired private InvoiceRepository invoiceRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createInvoice_calculatesTotalsServerSide_ignoringAnyClientSuppliedPricing() {
        Business business = createBusiness("Auto Care", "autocare@example.com", "Tamil Nadu");
        BusinessSettings settings = createSettings(business);
        Customer customer = createCustomer(business.getId(), "Ramesh", "Tamil Nadu"); // same state -> CGST/SGST split
        Product product = createProduct(business.getId(), "Engine Oil", new BigDecimal("500.00"), new BigDecimal("18"));

        setTenantContext(business.getId(), UUID.randomUUID());

        // The request only supplies quantity - price/tax must come from the Product record, not from here.
        InvoiceItemRequest itemRequest = new InvoiceItemRequest(product.getId(), null, new BigDecimal("2"), null, null);
        InvoiceRequest request = new InvoiceRequest(
                customer.getId(), LocalDate.now(), null, List.of(itemRequest), null, null, null);

        InvoiceResponse response = invoiceService.create(request);

        // 2 x 500 = 1000 subtotal, no discount, 18% tax = 180, split evenly CGST/SGST since same state
        assertThat(response.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(response.taxableAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.totalTax()).isEqualByComparingTo("180.00");
        assertThat(response.cgstAmount()).isEqualByComparingTo("90.00");
        assertThat(response.sgstAmount()).isEqualByComparingTo("90.00");
        assertThat(response.igstAmount()).isEqualByComparingTo("0.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("1180.00");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.invoiceNumber()).isNull(); // not assigned until issued
    }

    @Test
    void issueInvoice_assignsSequentialNumberAndIsSafeAcrossTwoInvoicesForTheSameBusiness() {
        Business business = createBusiness("Auto Care 2", "autocare2@example.com", "Kerala");
        createSettings(business);
        Customer customer = createCustomer(business.getId(), "Suresh", "Kerala");
        Product product = createProduct(business.getId(), "Brake Pad", new BigDecimal("300.00"), new BigDecimal("12"));

        setTenantContext(business.getId(), UUID.randomUUID());

        InvoiceResponse first = invoiceService.create(new InvoiceRequest(
                customer.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(product.getId(), null, BigDecimal.ONE, null, null)), null, null, null));
        InvoiceResponse second = invoiceService.create(new InvoiceRequest(
                customer.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(product.getId(), null, BigDecimal.ONE, null, null)), null, null, null));

        InvoiceResponse issuedFirst = invoiceService.issue(first.id());
        InvoiceResponse issuedSecond = invoiceService.issue(second.id());

        assertThat(issuedFirst.invoiceNumber()).isNotNull();
        assertThat(issuedSecond.invoiceNumber()).isNotNull();
        assertThat(issuedFirst.invoiceNumber()).isNotEqualTo(issuedSecond.invoiceNumber());
        assertThat(issuedFirst.status()).isEqualTo("ISSUED");
    }

    @Test
    void invoiceFromBusinessA_cannotBeFetchedUsingBusinessBId() {
        Business businessA = createBusiness("Shop A", "shopa-inv@example.com", "Karnataka");
        Business businessB = createBusiness("Shop B", "shopb-inv@example.com", "Karnataka");
        createSettings(businessA);
        Customer customerA = createCustomer(businessA.getId(), "Customer A", "Karnataka");
        Product productA = createProduct(businessA.getId(), "Item A", new BigDecimal("100.00"), BigDecimal.ZERO);

        setTenantContext(businessA.getId(), UUID.randomUUID());
        InvoiceResponse invoice = invoiceService.create(new InvoiceRequest(
                customerA.getId(), LocalDate.now(), null,
                List.of(new InvoiceItemRequest(productA.getId(), null, BigDecimal.ONE, null, null)), null, null, null));

        Optional<Invoice> crossTenant = invoiceRepository.findByIdAndBusinessId(invoice.id(), businessB.getId());
        assertThat(crossTenant).isEmpty();

        Optional<Invoice> sameTenant = invoiceRepository.findByIdAndBusinessId(invoice.id(), businessA.getId());
        assertThat(sameTenant).isPresent();
    }

    // -----------------------------------------------------------------
    private void setTenantContext(UUID businessId, UUID userId) {
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(userId, businessId, "test@example.com", "OWNER"));
    }

    private Business createBusiness(String name, String email, String state) {
        Business business = new Business();
        business.setBusinessName(name);
        business.setOwnerName(name + " Owner");
        business.setEmail(email);
        business.setPhone("9999999999");
        business.setState(state);
        business.setStatus(BusinessStatus.ACTIVE);
        return businessRepository.save(business);
    }

    private BusinessSettings createSettings(Business business) {
        BusinessSettings settings = new BusinessSettings();
        settings.setBusiness(business);
        return businessSettingsRepository.save(settings);
    }

    private Customer createCustomer(UUID businessId, String name, String state) {
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setCustomerName(name);
        customer.setState(state);
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
