package com.saasbilling.repository;

import com.saasbilling.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // Every read/write that returns a single Customer to a caller MUST
    // go through this (or an equivalent business_id-scoped) method -
    // never findById(id) alone. This is what prevents Business A from
    // reading/editing Business B's customer by guessing/reusing an id.
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * Search across name/phone/email/gstin, scoped to the tenant, with
     * optional free-text keyword and optional status filter. Both filters
     * are optional so the same query backs both "browse all" and "search".
     */
    @Query("""
            select c from Customer c
            where c.businessId = :businessId
              and (:status is null or c.status = :status)
              and (
                    :keyword is null or :keyword = ''
                    or lower(c.customerName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.phone, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.email, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(c.gstin, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Customer> search(@Param("businessId") UUID businessId,
                           @Param("keyword") String keyword,
                           @Param("status") com.saasbilling.entity.ActiveStatus status,
                           Pageable pageable);
}
