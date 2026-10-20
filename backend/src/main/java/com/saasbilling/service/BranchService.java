package com.saasbilling.service;

import com.saasbilling.dto.branch.BranchDtos.BranchRequest;
import com.saasbilling.dto.branch.BranchDtos.BranchResponse;
import com.saasbilling.entity.Branch;
import com.saasbilling.entity.Business;
import com.saasbilling.entity.BusinessSettings;
import com.saasbilling.exception.ForbiddenOperationException;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.BranchRepository;
import com.saasbilling.repository.BusinessRepository;
import com.saasbilling.repository.BusinessSettingsRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final AuditLogService auditLogService;

    public BranchService(BranchRepository branchRepository, BusinessRepository businessRepository,
                         BusinessSettingsRepository settingsRepository, AuditLogService auditLogService) {
        this.branchRepository = branchRepository;
        this.businessRepository = businessRepository;
        this.settingsRepository = settingsRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        return branchRepository.findByBusinessIdAndStatusOrderByDefaultBranchDescBranchNameAsc(
                        TenantContext.currentBusinessId(), "ACTIVE")
                .stream().map(BranchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listAll() {
        return branchRepository.findByBusinessIdOrderByDefaultBranchDescBranchNameAsc(TenantContext.currentBusinessId())
                .stream().map(BranchResponse::from).toList();
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        Branch branch = new Branch();
        branch.setBusinessId(TenantContext.currentBusinessId());
        apply(branch, request);
        branch = branchRepository.save(branch);
        Business business = businessRepository.findById(branch.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        BusinessSettings settings = new BusinessSettings();
        settings.setBusiness(business);
        settings.setBranchId(branch.getId());
        settingsRepository.save(settings);
        auditLogService.record(branch.getBusinessId(), TenantContext.currentUserId(), "BRANCH_CREATED",
                "BRANCH", branch.getId(), null, branch.getBranchName());
        return BranchResponse.from(branch);
    }

    @Transactional
    public BranchResponse update(UUID id, BranchRequest request) {
        Branch branch = getOwned(id);
        String oldName = branch.getBranchName();
        apply(branch, request);
        branch = branchRepository.save(branch);
        auditLogService.record(branch.getBusinessId(), TenantContext.currentUserId(), "BRANCH_UPDATED",
                "BRANCH", branch.getId(), oldName, branch.getBranchName());
        return BranchResponse.from(branch);
    }

    @Transactional
    public void deactivate(UUID id) {
        Branch branch = getOwned(id);
        if (branch.isDefaultBranch()) {
            throw new ForbiddenOperationException("The default branch cannot be deactivated");
        }
        branch.setStatus("INACTIVE");
        branchRepository.save(branch);
        auditLogService.record(branch.getBusinessId(), TenantContext.currentUserId(), "BRANCH_DEACTIVATED",
                "BRANCH", branch.getId(), "ACTIVE", "INACTIVE");
    }

    @Transactional
    public BranchResponse reactivate(UUID id) {
        Branch branch = branchRepository.findByIdAndBusinessId(id, TenantContext.currentBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        if ("ACTIVE".equals(branch.getStatus())) return BranchResponse.from(branch);
        branch.setStatus("ACTIVE");
        branch = branchRepository.save(branch);
        auditLogService.record(branch.getBusinessId(), TenantContext.currentUserId(), "BRANCH_REACTIVATED",
                "BRANCH", branch.getId(), "INACTIVE", "ACTIVE");
        return BranchResponse.from(branch);
    }

    private Branch getOwned(UUID id) {
        return branchRepository.findByIdAndBusinessIdAndStatus(id, TenantContext.currentBusinessId(), "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private void apply(Branch branch, BranchRequest request) {
        branch.setBranchName(request.branchName().trim());
        branch.setAddressLine(request.addressLine());
        branch.setCity(request.city());
        branch.setState(request.state());
        branch.setPincode(request.pincode());
        branch.setCountry(request.country() == null || request.country().isBlank() ? "India" : request.country());
        branch.setPhone(request.phone());
        branch.setEmail(request.email());
        branch.setGstin(request.gstin());
    }
}
