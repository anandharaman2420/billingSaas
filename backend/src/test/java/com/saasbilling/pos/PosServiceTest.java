package com.saasbilling.pos;

import com.saasbilling.dto.pos.PosCatalogItem;
import com.saasbilling.dto.pos.PosCheckoutRequest;
import com.saasbilling.dto.pos.PosCheckoutResponse;
import com.saasbilling.dto.pos.PosItemRequest;
import com.saasbilling.entity.*;
import com.saasbilling.repository.*;
import com.saasbilling.security.TenantContext;
import com.saasbilling.service.PosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PosServiceTest {

    @Autowired private BusinessRepository businessRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private BusinessSettingsRepository businessSettingsRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PosService posService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void posCheckout_walkInCustomer_completesSaleAndDeductsStock() {
        Business business = createBusiness("Retail Hub", "retail@example.com");
        createSettings(business);
        User cashier = createUser(business, "cashier@example.com", "Rajesh Cashier");
        Product product = createProduct(business.getId(), "Notebook A5", "NOTE-01",
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("20.00"));

        setTenantContext(business.getId(), cashier.getId());

        PosItemRequest item = new PosItemRequest(product.getId(), null, new BigDecimal("2.00"), null, null);
        PosCheckoutRequest checkoutReq = new PosCheckoutRequest(
                null,
                "Walk-in Customer",
                null,
                List.of(item),
                null,
                PaymentMethod.CASH,
                new BigDecimal("500.00"), // Tendered 500 for 200 bill
                null,
                null,
                "Counter 1 sale"
        );

        PosCheckoutResponse response = posService.checkout(checkoutReq);

        // 1. Verify invoice issued and paid with separate BILL prefix
        assertThat(response.invoice()).isNotNull();
        assertThat(response.invoice().status()).isEqualTo("PAID");
        assertThat(response.invoice().invoiceNumber()).isNotNull();
        assertThat(response.invoice().invoiceNumber()).startsWith("BILL-");
        assertThat(response.receipt().billNo()).startsWith("BILL-");
        assertThat(response.invoice().grandTotal()).isEqualByComparingTo("200.00");
        assertThat(response.invoice().amountPaid()).isEqualByComparingTo("200.00");

        // 2. Verify change calculation
        assertThat(response.changeAmount()).isEqualByComparingTo("300.00");

        // 3. Verify stock deduction (20 - 2 = 18)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualByComparingTo("18.00");

        // 4. Verify receipt data
        assertThat(response.receipt()).isNotNull();
        assertThat(response.receipt().cashierName()).isEqualTo("Rajesh Cashier");
        assertThat(response.receipt().businessName()).isEqualTo("Retail Hub");
        assertThat(response.receipt().items()).hasSize(1);
        assertThat(response.receipt().items().get(0).itemName()).isEqualTo("Notebook A5");
        assertThat(response.receipt().paymentMethod()).isEqualTo("CASH");
        assertThat(response.receipt().changeAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void posCheckout_customBillFormat_generatesSequentialBillNo() {
        Business business = createBusiness("Supermarket", "super@example.com");
        BusinessSettings settings = createSettings(business);
        settings.setPosPrefix("BILL");
        settings.setPosNumberFormat("BILL-YYYY-00001");
        settings.setPosNextSequence(3L); // sequence 3
        businessSettingsRepository.save(settings);

        User cashier = createUser(business, "cashier2@example.com", "Ravi");
        Product product = createProduct(business.getId(), "Apple 1kg", "APP-01",
                new BigDecimal("150.00"), BigDecimal.ZERO, new BigDecimal("50.00"));

        setTenantContext(business.getId(), cashier.getId());

        PosItemRequest item = new PosItemRequest(product.getId(), null, BigDecimal.ONE, null, null);
        PosCheckoutRequest checkoutReq = new PosCheckoutRequest(
                null, "Walk-in Customer", null, List.of(item), null, PaymentMethod.CASH,
                new BigDecimal("200.00"), null, null, null
        );

        PosCheckoutResponse response = posService.checkout(checkoutReq);

        int currentYear = java.time.LocalDate.now().getYear();
        String expectedBillNo = "BILL-" + currentYear + "-00003";

        assertThat(response.invoice().invoiceNumber()).isEqualTo(expectedBillNo);
        assertThat(response.receipt().billNo()).isEqualTo(expectedBillNo);

        // Next bill should be 00004
        PosCheckoutResponse response2 = posService.checkout(checkoutReq);
        String expectedBillNo2 = "BILL-" + currentYear + "-00004";
        assertThat(response2.invoice().invoiceNumber()).isEqualTo(expectedBillNo2);

        // Ensure regular invoice sequence remains untouched at 1
        BusinessSettings updatedSettings = businessSettingsRepository.findByBusinessId(business.getId()).orElseThrow();
        assertThat(updatedSettings.getInvoiceNextSequence()).isEqualTo(1L);
        assertThat(updatedSettings.getPosNextSequence()).isEqualTo(5L);
    }

    @Test
    void posCatalog_returnsItemsAndFiltersByKeyword() {
        Business business = createBusiness("Grocery Shop", "grocery@example.com");
        createSettings(business);
        User cashier = createUser(business, "user@example.com", "Anita");
        createProduct(business.getId(), "Basmati Rice 1kg", "RICE-01", new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("50.00"));
        createProduct(business.getId(), "Wheat Flour 5kg", "FLOUR-05", new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("30.00"));

        setTenantContext(business.getId(), cashier.getId());

        List<PosCatalogItem> allItems = posService.getCatalog(null, null);
        assertThat(allItems).hasSize(2);

        List<PosCatalogItem> searchResults = posService.getCatalog("rice", null);
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).name()).isEqualTo("Basmati Rice 1kg");
    }

    // -----------------------------------------------------------------
    private void setTenantContext(UUID businessId, UUID userId) {
        TenantContext.set(new TenantContext.AuthenticatedPrincipal(userId, businessId,
                "user@example.com", "STAFF", defaultBranchId(businessId)));
    }

    private Business createBusiness(String name, String email) {
        Business b = new Business();
        b.setBusinessName(name);
        b.setOwnerName("Owner " + name);
        b.setEmail(email);
        b.setPhone("9876543210");
        b.setAddressLine("123 Market Road");
        b.setCity("Chennai");
        b.setState("Tamil Nadu");
        b.setPincode("600001");
        b.setStatus(BusinessStatus.ACTIVE);
        b = businessRepository.save(b);
        Branch branch = new Branch();
        branch.setBusinessId(b.getId());
        branch.setBranchName("Main Branch");
        branch.setAddressLine(b.getAddressLine());
        branch.setCity(b.getCity());
        branch.setState(b.getState());
        branch.setPincode(b.getPincode());
        branch.setPhone(b.getPhone());
        branch.setEmail(b.getEmail());
        branch.setDefaultBranch(true);
        branchRepository.save(branch);
        return b;
    }

    private BusinessSettings createSettings(Business b) {
        BusinessSettings s = new BusinessSettings();
        s.setBusiness(b);
        s.setBranchId(defaultBranchId(b.getId()));
        s.setInvoicePrefix("INV");
        s.setInvoiceNumberFormat("{PREFIX}-{YEAR}-{SEQ:00000}");
        s.setInvoiceNextSequence(1L);
        s.setFinancialYearStartMonth((short) 4);
        s.setDefaultDueDays(7);
        s.setCurrency("INR");
        s.setTaxMode("EXCLUSIVE");
        return businessSettingsRepository.save(s);
    }

    private User createUser(Business b, String email, String name) {
        User u = new User();
        u.setBusiness(b);
        u.setEmail(email);
        u.setFullName(name);
        u.setPasswordHash("{bcrypt}xyz");
        u.setRole(UserRole.STAFF);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private Product createProduct(UUID businessId, String name, String sku, BigDecimal price, BigDecimal taxRate, BigDecimal stock) {
        Product p = new Product();
        p.setBusinessId(businessId);
        p.setBranchId(defaultBranchId(businessId));
        p.setProductName(name);
        p.setSku(sku);
        p.setSellingPrice(price);
        p.setTaxRatePercent(taxRate);
        p.setStockQuantity(stock);
        p.setStatus(ActiveStatus.ACTIVE);
        return productRepository.save(p);
    }

    private UUID defaultBranchId(UUID businessId) {
        return branchRepository.findFirstByBusinessIdAndDefaultBranchTrue(businessId).orElseThrow().getId();
    }
}
