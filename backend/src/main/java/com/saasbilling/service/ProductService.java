package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.product.ProductRequest;
import com.saasbilling.dto.product.ProductResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.entity.Product;
import com.saasbilling.exception.DuplicateResourceException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.ProductRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    public ProductService(ProductRepository productRepository, AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        assertSkuNotDuplicate(businessId, request.sku(), null);

        Product product = new Product();
        product.setBusinessId(businessId);
        applyRequest(product, request);
        product = productRepository.save(product);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PRODUCT_CREATED",
                "PRODUCT", product.getId(), null, null);

        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        Product product = getOwnedOrThrow(id, businessId);
        assertSkuNotDuplicate(businessId, request.sku(), id);

        applyRequest(product, request);
        product = productRepository.save(product);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PRODUCT_UPDATED",
                "PRODUCT", product.getId(), null, null);

        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return ProductResponse.from(getOwnedOrThrow(id, TenantContext.currentBusinessId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, ActiveStatus status, UUID categoryId, Pageable pageable) {
        Page<Product> page = productRepository.search(TenantContext.currentBusinessId(), keyword, status, categoryId, pageable);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> lowStock(Pageable pageable) {
        Page<Product> page = productRepository.findLowStock(TenantContext.currentBusinessId(), pageable);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Product product = getOwnedOrThrow(id, businessId);
        product.setStatus(ActiveStatus.INACTIVE);
        productRepository.save(product);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PRODUCT_DEACTIVATED",
                "PRODUCT", product.getId(), null, null);
    }

    @Transactional
    public void reactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Product product = getOwnedOrThrow(id, businessId);
        product.setStatus(ActiveStatus.ACTIVE);
        productRepository.save(product);

        auditLogService.record(businessId, TenantContext.currentUserId(), "PRODUCT_REACTIVATED",
                "PRODUCT", product.getId(), null, null);
    }

    // -----------------------------------------------------------------
    private Product getOwnedOrThrow(UUID id, UUID businessId) {
        return productRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void assertSkuNotDuplicate(UUID businessId, String sku, UUID excludingId) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        boolean duplicate = (excludingId == null)
                ? productRepository.existsByBusinessIdAndSkuIgnoreCase(businessId, sku)
                : productRepository.existsByBusinessIdAndSkuIgnoreCaseAndIdNot(businessId, sku, excludingId);

        if (duplicate) {
            throw new DuplicateResourceException("A product with SKU '" + sku + "' already exists");
        }
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setProductName(request.productName());
        product.setSku((request.sku() == null || request.sku().isBlank()) ? null : request.sku());
        product.setCategoryId(request.categoryId());
        product.setDescription(request.description());
        product.setUnit(request.unit());
        product.setPurchasePrice(request.purchasePrice());
        product.setSellingPrice(request.sellingPrice());
        product.setTaxRatePercent(request.taxRatePercent());
        product.setStockQuantity(request.stockQuantity());
        product.setMinimumStockLevel(request.minimumStockLevel());
    }
}
