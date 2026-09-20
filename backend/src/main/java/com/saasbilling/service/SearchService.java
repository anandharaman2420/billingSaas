package com.saasbilling.service;

import com.saasbilling.dto.search.SearchDtos.*;
import com.saasbilling.repository.BillableServiceRepository;
import com.saasbilling.repository.CustomerRepository;
import com.saasbilling.repository.InvoiceRepository;
import com.saasbilling.repository.ProductRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Backs the app's single top-bar search box (spec section 22). Each
 * source is capped to a handful of hits and reuses the same
 * tenant-scoped, paginated search queries each module's own list page
 * already uses - this endpoint just fans out to all of them and trims
 * the results, rather than introducing a separate full-text index.
 */
@Service
public class SearchService {

    private static final int HITS_PER_SOURCE = 5;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final BillableServiceRepository serviceRepository;
    private final InvoiceRepository invoiceRepository;

    public SearchService(CustomerRepository customerRepository,
                          ProductRepository productRepository,
                          BillableServiceRepository serviceRepository,
                          InvoiceRepository invoiceRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.serviceRepository = serviceRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public GlobalSearchResponse search(String keyword) {
        UUID businessId = TenantContext.currentBusinessId();
        var pageable = PageRequest.of(0, HITS_PER_SOURCE);

        if (keyword == null || keyword.isBlank()) {
            return new GlobalSearchResponse(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        }

        var customers = customerRepository.search(businessId, keyword, null, pageable).getContent().stream()
                .map(c -> new CustomerHit(c.getId(), c.getCustomerName(), c.getPhone()))
                .toList();

        var products = productRepository.search(businessId, keyword, null, null, pageable).getContent().stream()
                .map(p -> new ProductHit(p.getId(), p.getProductName(), p.getSku()))
                .toList();

        var services = serviceRepository.search(businessId, keyword, null, null, pageable).getContent().stream()
                .map(s -> new ServiceHit(s.getId(), s.getServiceName()))
                .toList();

        var invoices = invoiceRepository.search(businessId, keyword, null, null, null, null, pageable).getContent().stream()
                .map(i -> new InvoiceHit(i.getId(), i.getInvoiceNumber(), i.getStatus().name(), i.getGrandTotal()))
                .toList();

        return new GlobalSearchResponse(customers, products, services, invoices);
    }
}
