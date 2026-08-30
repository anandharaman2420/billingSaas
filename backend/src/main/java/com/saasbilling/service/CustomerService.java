package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.customer.CustomerRequest;
import com.saasbilling.dto.customer.CustomerResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.entity.Customer;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    public CustomerService(CustomerRepository customerRepository, AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        applyRequest(customer, request);
        customer = customerRepository.save(customer);

        auditLogService.record(businessId, TenantContext.currentUserId(), "CUSTOMER_CREATED",
                "CUSTOMER", customer.getId(), null, null);

        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        Customer customer = getOwnedOrThrow(id, businessId);

        applyRequest(customer, request);
        customer = customerRepository.save(customer);

        auditLogService.record(businessId, TenantContext.currentUserId(), "CUSTOMER_UPDATED",
                "CUSTOMER", customer.getId(), null, null);

        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer customer = getOwnedOrThrow(id, TenantContext.currentBusinessId());
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String keyword, ActiveStatus status, Pageable pageable) {
        Page<Customer> page = customerRepository.search(TenantContext.currentBusinessId(), keyword, status, pageable);
        return PageResponse.from(page.map(CustomerResponse::from));
    }

    /**
     * Soft-delete: customers are never hard-deleted, since they may be
     * referenced by historical invoices (spec section 42: never delete
     * important financial records). Marking INACTIVE hides them from
     * the default active list while preserving invoice history integrity.
     */
    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Customer customer = getOwnedOrThrow(id, businessId);
        customer.setStatus(ActiveStatus.INACTIVE);
        customerRepository.save(customer);

        auditLogService.record(businessId, TenantContext.currentUserId(), "CUSTOMER_DEACTIVATED",
                "CUSTOMER", customer.getId(), null, null);
    }

    @Transactional
    public void reactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Customer customer = getOwnedOrThrow(id, businessId);
        customer.setStatus(ActiveStatus.ACTIVE);
        customerRepository.save(customer);

        auditLogService.record(businessId, TenantContext.currentUserId(), "CUSTOMER_REACTIVATED",
                "CUSTOMER", customer.getId(), null, null);
    }

    // -----------------------------------------------------------------
    private Customer getOwnedOrThrow(UUID id, UUID businessId) {
        return customerRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setCustomerName(request.customerName());
        customer.setPhone(blankToNull(request.phone()));
        customer.setEmail(blankToNull(request.email()));
        customer.setAddressLine(request.addressLine());
        customer.setCity(request.city());
        customer.setState(request.state());
        customer.setPincode(blankToNull(request.pincode()));
        customer.setGstin(blankToNull(request.gstin()));
        customer.setNotes(request.notes());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
