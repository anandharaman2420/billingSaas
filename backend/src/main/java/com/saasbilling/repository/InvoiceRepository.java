package com.saasbilling.repository;

import com.saasbilling.entity.Invoice;
import com.saasbilling.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // List/search queries deliberately do NOT fetch items (keeps the
    // list endpoint light); getById below fetches items eagerly since
    // the detail/PDF views always need them.
    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    @EntityGraph(attributePaths = "items")
    @Query("select i from Invoice i where i.id = :id and i.businessId = :businessId")
    Optional<Invoice> findWithItemsByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    boolean existsByBusinessIdAndInvoiceNumber(UUID businessId, String invoiceNumber);

    @Query("""
            select i from Invoice i
            where i.businessId = :businessId
              and (:status is null or i.status = :status)
              and (:customerId is null or i.customerId = :customerId)
              and (:fromDate is null or i.invoiceDate >= :fromDate)
              and (:toDate is null or i.invoiceDate <= :toDate)
              and (:keyword is null or :keyword = '' or lower(i.invoiceNumber) like lower(concat('%', :keyword, '%')))
            """)
    Page<Invoice> search(@Param("businessId") UUID businessId,
                          @Param("keyword") String keyword,
                          @Param("status") InvoiceStatus status,
                          @Param("customerId") UUID customerId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          Pageable pageable);

    // Used by a future scheduled job (or an on-read check) to flip
    // ISSUED/PARTIALLY_PAID invoices past their due date to OVERDUE.
    @Query("""
            select i from Invoice i
            where i.businessId = :businessId
              and i.status in (com.saasbilling.entity.InvoiceStatus.ISSUED, com.saasbilling.entity.InvoiceStatus.PARTIALLY_PAID)
              and i.dueDate is not null
              and i.dueDate < :asOf
            """)
    java.util.List<Invoice> findOverdueCandidates(@Param("businessId") UUID businessId, @Param("asOf") LocalDate asOf);
}
