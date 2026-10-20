package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.service.ServiceRequest;
import com.saasbilling.dto.service.ServiceResponse;
import com.saasbilling.entity.ActiveStatus;
import com.saasbilling.entity.BillableService;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BillableServiceRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BillableServiceService {

    private final BillableServiceRepository serviceRepository;
    private final AuditLogService auditLogService;

    public BillableServiceService(BillableServiceRepository serviceRepository, AuditLogService auditLogService) {
        this.serviceRepository = serviceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        BillableService service = new BillableService();
        service.setBusinessId(businessId);
        service.setBranchId(TenantContext.currentBranchId());
        applyRequest(service, request);
        service = serviceRepository.save(service);

        auditLogService.record(businessId, TenantContext.currentUserId(), "SERVICE_CREATED",
                "SERVICE", service.getId(), null, null);

        return ServiceResponse.from(service);
    }

    @Transactional
    public ServiceResponse update(UUID id, ServiceRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        BillableService service = getOwnedOrThrow(id, businessId, TenantContext.currentBranchId());

        applyRequest(service, request);
        service = serviceRepository.save(service);

        auditLogService.record(businessId, TenantContext.currentUserId(), "SERVICE_UPDATED",
                "SERVICE", service.getId(), null, null);

        return ServiceResponse.from(service);
    }

    @Transactional(readOnly = true)
    public ServiceResponse getById(UUID id) {
        return ServiceResponse.from(getOwnedOrThrow(id, TenantContext.currentBusinessId(), TenantContext.currentBranchId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> search(String keyword, ActiveStatus status, UUID categoryId, Pageable pageable) {
        Page<BillableService> page = serviceRepository.searchByBranch(TenantContext.currentBusinessId(),
                TenantContext.currentBranchId(), keyword, status, categoryId, pageable);
        return PageResponse.from(page.map(ServiceResponse::from));
    }

    @Transactional
    public void deactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        BillableService service = getOwnedOrThrow(id, businessId, TenantContext.currentBranchId());
        service.setStatus(ActiveStatus.INACTIVE);
        serviceRepository.save(service);

        auditLogService.record(businessId, TenantContext.currentUserId(), "SERVICE_DEACTIVATED",
                "SERVICE", service.getId(), null, null);
    }

    @Transactional
    public void reactivate(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        BillableService service = getOwnedOrThrow(id, businessId, TenantContext.currentBranchId());
        service.setStatus(ActiveStatus.ACTIVE);
        serviceRepository.save(service);

        auditLogService.record(businessId, TenantContext.currentUserId(), "SERVICE_REACTIVATED",
                "SERVICE", service.getId(), null, null);
    }

    // -----------------------------------------------------------------
    private BillableService getOwnedOrThrow(UUID id, UUID businessId, UUID branchId) {
        return serviceRepository.findByIdAndBusinessIdAndBranchId(id, businessId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    private void applyRequest(BillableService service, ServiceRequest request) {
        service.setServiceName(request.serviceName());
        service.setDescription(request.description());
        service.setSacCode((request.sacCode() == null || request.sacCode().isBlank()) ? null : request.sacCode());
        service.setPrice(request.price());
        service.setTaxRatePercent(request.taxRatePercent());
        service.setCategoryId(request.categoryId());
    }
}
