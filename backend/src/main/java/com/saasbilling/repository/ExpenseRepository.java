package com.saasbilling.repository;

import com.saasbilling.entity.Expense;
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

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndBusinessId(UUID id, UUID businessId);

    @Query("""
            select e from Expense e
            where e.businessId = :businessId
              and (:categoryId is null or e.categoryId = :categoryId)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
              and (:keyword is null or :keyword = '' or lower(e.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Expense> search(@Param("businessId") UUID businessId,
                          @Param("keyword") String keyword,
                          @Param("categoryId") UUID categoryId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.businessId = :businessId and e.expenseDate >= :fromDate and e.expenseDate <= :toDate")
    BigDecimal sumInRange(@Param("businessId") UUID businessId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("""
            select e.categoryId as categoryId, coalesce(sum(e.amount), 0) as total
            from Expense e
            where e.businessId = :businessId and e.expenseDate >= :fromDate and e.expenseDate <= :toDate
            group by e.categoryId
            """)
    List<CategoryTotal> sumByCategoryInRange(@Param("businessId") UUID businessId,
                                              @Param("fromDate") LocalDate fromDate,
                                              @Param("toDate") LocalDate toDate);

    interface CategoryTotal {
        UUID getCategoryId();
        BigDecimal getTotal();
    }
}
