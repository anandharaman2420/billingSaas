package com.saasbilling.repository;

import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.entity.BillableService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BillableServiceRepository extends JpaRepository<BillableService, UUID> {

    Optional<BillableService> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select s from BillableService s
            where s.businessId = :businessId
              and (:status is null or s.status = :status)
              and (:categoryId is null or s.categoryId = :categoryId)
              and (
                    :keyword is null or :keyword = ''
                    or lower(s.serviceName) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<BillableService> search(@Param("businessId") UUID businessId,
                                  @Param("keyword") String keyword,
                                  @Param("status") ActiveStatus status,
                                  @Param("categoryId") UUID categoryId,
                                  Pageable pageable);
}
