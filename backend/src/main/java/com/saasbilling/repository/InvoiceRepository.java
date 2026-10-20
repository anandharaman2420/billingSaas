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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    // List/search queries deliberately do NOT fetch items (keeps the
    // list endpoint light); getById below fetches items eagerly since
    // the detail/PDF views always need them.
    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Invoice> findByIdAndBusinessIdAndBranchId(UUID id, UUID businessId, UUID branchId);

    @EntityGraph(attributePaths = "items")
    @Query("select i from Invoice i where i.id = :id and i.businessId = :businessId")
    Optional<Invoice> findWithItemsByIdAndBusinessId(@Param("id") UUID id, @Param("businessId") UUID businessId);

    @EntityGraph(attributePaths = "items")
    @Query("select i from Invoice i where i.id = :id and i.businessId = :businessId and i.branchId = :branchId")
    Optional<Invoice> findWithItemsByIdAndBusinessIdAndBranchId(@Param("id") UUID id,
                                                                 @Param("businessId") UUID businessId,
                                                                 @Param("branchId") UUID branchId);

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

    @Query("""
            select i from Invoice i
            where i.businessId = :businessId and i.branchId = :branchId
              and (:status is null or i.status = :status)
              and (:customerId is null or i.customerId = :customerId)
              and (:fromDate is null or i.invoiceDate >= :fromDate)
              and (:toDate is null or i.invoiceDate <= :toDate)
              and (:keyword is null or :keyword = '' or lower(i.invoiceNumber) like lower(concat('%', :keyword, '%')))
            """)
    Page<Invoice> searchByBranch(@Param("businessId") UUID businessId,
                                  @Param("branchId") UUID branchId,
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

    List<Invoice> findByBusinessIdAndStatus(UUID businessId, InvoiceStatus status);

    @Query("""
            select i from Invoice i
            where i.businessId = :businessId
              and i.status in (com.saasbilling.entity.InvoiceStatus.ISSUED, com.saasbilling.entity.InvoiceStatus.PARTIALLY_PAID)
              and i.dueDate = :dueDate
            """)
    List<Invoice> findDueOnDate(@Param("businessId") UUID businessId, @Param("dueDate") LocalDate dueDate);

    // --- Dashboard aggregates (spec section 7) ---

    @Query("""
            select coalesce(sum(i.grandTotal), 0) from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            """)
    java.math.BigDecimal sumSalesInRange(@Param("businessId") UUID businessId,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    @Query("""
            select coalesce(sum(i.grandTotal - i.amountPaid), 0) from Invoice i
            where i.businessId = :businessId
              and i.status in (com.saasbilling.entity.InvoiceStatus.ISSUED, com.saasbilling.entity.InvoiceStatus.PARTIALLY_PAID, com.saasbilling.entity.InvoiceStatus.OVERDUE)
            """)
    java.math.BigDecimal sumPendingBalance(@Param("businessId") UUID businessId);

    @Query("""
            select coalesce(sum(i.amountPaid), 0) from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
            """)
    java.math.BigDecimal sumPaidAmount(@Param("businessId") UUID businessId);

    @Query("""
            select count(i) from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
            """)
    long countIssuedInvoices(@Param("businessId") UUID businessId);

    @Query("""
            select count(i) from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            """)
    long countIssuedInvoicesInRange(@Param("businessId") UUID businessId,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    @Query("""
            select coalesce(sum(i.grandTotal), 0) from Invoice i
            where i.branchId = :branchId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            """)
    java.math.BigDecimal sumSalesInRangeByBranch(@Param("branchId") UUID branchId,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate);

    @Query("""
            select coalesce(sum(i.grandTotal - i.amountPaid), 0) from Invoice i
            where i.branchId = :branchId
              and i.status in (com.saasbilling.entity.InvoiceStatus.ISSUED, com.saasbilling.entity.InvoiceStatus.PARTIALLY_PAID, com.saasbilling.entity.InvoiceStatus.OVERDUE)
            """)
    java.math.BigDecimal sumPendingBalanceByBranch(@Param("branchId") UUID branchId);

    @Query("""
            select coalesce(sum(i.amountPaid), 0) from Invoice i
            where i.branchId = :branchId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
            """)
    java.math.BigDecimal sumPaidAmountByBranch(@Param("branchId") UUID branchId);

    @Query("""
            select count(i) from Invoice i
            where i.branchId = :branchId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
            """)
    long countIssuedInvoicesByBranch(@Param("branchId") UUID branchId);

    @Query("""
            select count(i) from Invoice i
            where i.branchId = :branchId and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
            """)
    long countIssuedInvoicesInRangeByBranch(@Param("branchId") UUID branchId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);


    // --- Report aggregates ---

    interface DailySalesRow {
        LocalDate getInvoiceDate();
        java.math.BigDecimal getTotal();
        long getCount();
    }

    @Query("""
            select i.invoiceDate as invoiceDate, sum(i.grandTotal) as total, count(i) as count
            from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            group by i.invoiceDate
            order by i.invoiceDate
            """)
    List<DailySalesRow> dailySalesInRange(@Param("businessId") UUID businessId,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate);

    @Query("""
            select i.invoiceDate as invoiceDate, sum(i.grandTotal) as total, count(i) as count
            from Invoice i
            where i.branchId = :branchId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            group by i.invoiceDate
            order by i.invoiceDate
            """)
    List<DailySalesRow> dailySalesInRangeByBranch(@Param("branchId") UUID branchId,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);

    interface StatusCountRow {
        InvoiceStatus getStatus();
        long getCount();
    }

    @Query("select i.status as status, count(i) as count from Invoice i where i.businessId = :businessId group by i.status")
    List<StatusCountRow> countByStatus(@Param("businessId") UUID businessId);

    @Query("select i.status as status, count(i) as count from Invoice i where i.branchId = :branchId group by i.status")
    List<StatusCountRow> countByStatusAndBranch(@Param("branchId") UUID branchId);

    interface CustomerSalesRow {
        UUID getCustomerId();
        java.math.BigDecimal getTotal();
        long getInvoiceCount();
    }

    @Query("""
            select i.customerId as customerId, sum(i.grandTotal) as total, count(i) as invoiceCount
            from Invoice i
            where i.businessId = :businessId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            group by i.customerId
            order by sum(i.grandTotal) desc
            """)
    List<CustomerSalesRow> topCustomersInRange(@Param("businessId") UUID businessId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate,
                                                org.springframework.data.domain.Pageable pageable);

    @Query("""
            select i.customerId as customerId, sum(i.grandTotal) as total, count(i) as invoiceCount
            from Invoice i
            where i.branchId = :branchId
              and i.status <> com.saasbilling.entity.InvoiceStatus.DRAFT
              and i.status <> com.saasbilling.entity.InvoiceStatus.CANCELLED
              and i.invoiceDate >= :fromDate and i.invoiceDate <= :toDate
            group by i.customerId
            order by sum(i.grandTotal) desc
            """)
    List<CustomerSalesRow> topCustomersInRangeByBranch(@Param("branchId") UUID branchId,
                                                        @Param("fromDate") LocalDate fromDate,
                                                        @Param("toDate") LocalDate toDate,
                                                        org.springframework.data.domain.Pageable pageable);
}
