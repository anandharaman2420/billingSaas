package com.saasbilling.repository;

import com.saasbilling.entity.BusinessSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, UUID> {

    Optional<BusinessSettings> findByBusinessId(UUID businessId);

    /**
     * Row-level lock (SELECT ... FOR UPDATE) on the business's settings
     * row. Used exclusively by invoice-number generation: the caller
     * reads invoiceNextSequence, builds the invoice number, increments
     * the counter, and saves - all inside one @Transactional method.
     * Concurrent requests for the same business serialize on this lock,
     * so two invoices can never be issued the same number. Requests for
     * *different* businesses never contend, since the lock is per-row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BusinessSettings s where s.business.id = :businessId")
    Optional<BusinessSettings> findByBusinessIdForUpdate(@Param("businessId") UUID businessId);
}
