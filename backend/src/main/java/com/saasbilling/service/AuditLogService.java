package com.saasbilling.service;

import com.saasbilling.entity.AuditLog;
import com.saasbilling.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes a row to audit_logs for significant business actions
 * (invoice created, payment recorded, settings changed, user login, etc).
 *
 * Kept deliberately simple for Phase 1: business_id/user_id/action/entity.
 * before/after JSON diffing is wired in as each domain module (invoices,
 * customers, ...) is built in later phases.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void record(UUID businessId, UUID userId, String action, String entityType, UUID entityId,
                        String beforeValueJson, String afterValueJson) {
        try {
            AuditLog entry = new AuditLog();
            entry.setBusinessId(businessId);
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setBeforeValue(beforeValueJson);
            entry.setAfterValue(afterValueJson);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit logging must never break the primary business operation.
            log.error("Failed to write audit log entry for action={} entity={}/{}", action, entityType, entityId, e);
        }
    }
}
