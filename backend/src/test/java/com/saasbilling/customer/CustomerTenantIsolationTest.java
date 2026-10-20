package com.saasbilling.customer;

import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessStatus;
import com.saasbilling.entity.Branch;
import com.saasbilling.entity.Customer;
import com.saasbilling.entity.Product;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.BranchRepository;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerTenantIsolationTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void customerFromBusinessA_cannotBeFetchedUsingBusinessBId() {
        Business businessA = createBusiness("Auto Shop A", "a@example.com");
        Business businessB = createBusiness("Auto Shop B", "b@example.com");

        Customer customer = new Customer();
        customer.setBusinessId(businessA.getId());
        customer.setBranchId(defaultBranch(businessA).getId());
        customer.setCustomerName("Ramesh Kumar");
        customer = customerRepository.save(customer);

        Optional<Customer> crossTenant = customerRepository.findByIdAndBusinessId(customer.getId(), businessB.getId());
        assertThat(crossTenant).isEmpty();

        Optional<Customer> sameTenant = customerRepository.findByIdAndBusinessId(customer.getId(), businessA.getId());
        assertThat(sameTenant).isPresent();
    }

    @Test
    void customerFromAnotherBranchOfSameBusiness_isNotVisible() {
        Business business = createBusiness("Multi Branch Shop", "branches@example.com");
        Branch main = defaultBranch(business);
        Branch other = new Branch();
        other.setBusinessId(business.getId());
        other.setBranchName("Second Branch");
        other = branchRepository.save(other);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setBranchId(main.getId());
        customer.setCustomerName("Branch Customer");
        customer = customerRepository.save(customer);

        assertThat(customerRepository.findByIdAndBusinessIdAndBranchId(
                customer.getId(), business.getId(), other.getId())).isEmpty();
        assertThat(customerRepository.findByIdAndBusinessIdAndBranchId(
                customer.getId(), business.getId(), main.getId())).isPresent();
    }

    @Test
    void sameSku_isAllowedAcrossDifferentBusinesses_butNotWithinTheSameBusiness() {
        Business businessA = createBusiness("Shop A", "shopa@example.com");
        Business businessB = createBusiness("Shop B", "shopb@example.com");

        Product productA = newProduct(businessA.getId(), "OIL-001");
        productRepository.save(productA);

        // Same SKU in a *different* business must be allowed - SKU uniqueness is per-tenant, not global.
        boolean duplicateAcrossTenants = productRepository.existsByBusinessIdAndSkuIgnoreCase(businessB.getId(), "OIL-001");
        assertThat(duplicateAcrossTenants).isFalse();

        // Same SKU *within* the same business must be flagged as a duplicate.
        boolean duplicateWithinTenant = productRepository.existsByBusinessIdAndSkuIgnoreCase(businessA.getId(), "OIL-001");
        assertThat(duplicateWithinTenant).isTrue();
    }

    private Business createBusiness(String name, String email) {
        Business business = new Business();
        business.setBusinessName(name);
        business.setOwnerName(name + " Owner");
        business.setEmail(email);
        business.setPhone("9999999999");
        business.setStatus(BusinessStatus.ACTIVE);
        business = businessRepository.save(business);
        Branch branch = new Branch();
        branch.setBusinessId(business.getId());
        branch.setBranchName("Main Branch");
        branch.setDefaultBranch(true);
        branchRepository.save(branch);
        return business;
    }

    private Branch defaultBranch(Business business) {
        return branchRepository.findFirstByBusinessIdAndDefaultBranchTrue(business.getId()).orElseThrow();
    }

    private Product newProduct(java.util.UUID businessId, String sku) {
        Product product = new Product();
        product.setBusinessId(businessId);
        product.setBranchId(defaultBranch(businessRepository.findById(businessId).orElseThrow()).getId());
        product.setProductName("Engine Oil");
        product.setSku(sku);
        product.setUnit("LTR");
        product.setPurchasePrice(BigDecimal.valueOf(200));
        product.setSellingPrice(BigDecimal.valueOf(300));
        return product;
    }
}
