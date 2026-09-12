package com.saasbilling.repository;

import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSkuIgnoreCase(UUID businessId, String sku);

    // Used when checking for duplicate SKU on update - excludes the product being edited.
    boolean existsByBusinessIdAndSkuIgnoreCaseAndIdNot(UUID businessId, String sku, UUID id);

    @Query("""
            select p from Product p
            where p.businessId = :businessId
              and (:status is null or p.status = :status)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (
                    :keyword is null or :keyword = ''
                    or lower(p.productName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(p.sku, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Product> search(@Param("businessId") UUID businessId,
                          @Param("keyword") String keyword,
                          @Param("status") ActiveStatus status,
                          @Param("categoryId") UUID categoryId,
                          Pageable pageable);

    @Query("""
            select p from Product p
            where p.businessId = :businessId
              and p.status = com.saasbilling.entity.ActiveStatus.ACTIVE
              and p.stockQuantity <= p.minimumStockLevel
            """)
    Page<Product> findLowStock(@Param("businessId") UUID businessId, Pageable pageable);
}
