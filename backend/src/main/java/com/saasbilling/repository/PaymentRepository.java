package com.saasbilling.repository;

import com.saasbilling.entity.Payment;
import com.saasbilling.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Payment> findByInvoiceIdAndVoidedFalseOrderByPaymentDateAsc(UUID invoiceId);

    /**
     * The authoritative source for an invoice's amount_paid: always the
     * sum of its non-voided payments, recomputed after every record/void
     * rather than incrementally adjusted, so it can never drift.
     */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.invoiceId = :invoiceId and p.voided = false")
    BigDecimal sumActiveAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);

    @Query("""
            select p from Payment p
            where p.businessId = :businessId
              and (:invoiceId is null or p.invoiceId = :invoiceId)
              and (:customerId is null or p.customerId = :customerId)
              and (:method is null or p.paymentMethod = :method)
              and (:fromDate is null or p.paymentDate >= :fromDate)
              and (:toDate is null or p.paymentDate <= :toDate)
              and (:includeVoided = true or p.voided = false)
            """)
    Page<Payment> search(@Param("businessId") UUID businessId,
                          @Param("invoiceId") UUID invoiceId,
                          @Param("customerId") UUID customerId,
                          @Param("method") PaymentMethod method,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          @Param("includeVoided") boolean includeVoided,
                          Pageable pageable);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.businessId = :businessId and p.voided = false
              and p.paymentDate >= :fromDate and p.paymentDate <= :toDate
            """)
    BigDecimal sumActiveAmountInRange(@Param("businessId") UUID businessId,
                                       @Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate);

    interface MethodTotalRow {
        PaymentMethod getPaymentMethod();
        BigDecimal getTotal();
        long getCount();
    }

    @Query("""
            select p.paymentMethod as paymentMethod, sum(p.amount) as total, count(p) as count
            from Payment p
            where p.businessId = :businessId and p.voided = false
              and p.paymentDate >= :fromDate and p.paymentDate <= :toDate
            group by p.paymentMethod
            """)
    List<MethodTotalRow> sumByMethodInRange(@Param("businessId") UUID businessId,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);
}
