package com.saasbilling.repository;

import com.saasbilling.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);
}
