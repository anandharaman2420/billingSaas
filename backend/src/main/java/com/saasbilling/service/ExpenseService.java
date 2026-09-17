package com.saasbilling.service;

import com.saasbilling.dto.common.PageResponse;
import com.saasbilling.dto.expense.ExpenseRequest;
import com.saasbilling.dto.expense.ExpenseResponse;
import com.saasbilling.entity.Expense;
import com.saasbilling.exception.ResourceNotFoundException;
import com.saasbilling.repository.ExpenseRepository;
import com.saasbilling.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;

    public ExpenseService(ExpenseRepository expenseRepository, AuditLogService auditLogService) {
        this.expenseRepository = expenseRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        UUID businessId = TenantContext.currentBusinessId();

        Expense expense = new Expense();
        expense.setBusinessId(businessId);
        expense.setRecordedByUserId(TenantContext.currentUserId());
        applyRequest(expense, request);
        expense = expenseRepository.save(expense);

        auditLogService.record(businessId, TenantContext.currentUserId(), "EXPENSE_CREATED",
                "EXPENSE", expense.getId(), null, null);

        return ExpenseResponse.from(expense);
    }

    @Transactional
    public ExpenseResponse update(UUID id, ExpenseRequest request) {
        UUID businessId = TenantContext.currentBusinessId();
        Expense expense = getOwnedOrThrow(id, businessId);

        applyRequest(expense, request);
        expense = expenseRepository.save(expense);

        auditLogService.record(businessId, TenantContext.currentUserId(), "EXPENSE_UPDATED",
                "EXPENSE", expense.getId(), null, null);

        return ExpenseResponse.from(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getById(UUID id) {
        return ExpenseResponse.from(getOwnedOrThrow(id, TenantContext.currentBusinessId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(String keyword, UUID categoryId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Expense> page = expenseRepository.search(TenantContext.currentBusinessId(), keyword, categoryId, fromDate, toDate, pageable);
        return PageResponse.from(page.map(ExpenseResponse::from));
    }

    /**
     * Expenses are financial records too, but unlike customers/products
     * there's no "deactivate" concept for a past expense entry - a
     * mistaken one is corrected by editing or, if truly wrong, deleted
     * outright. Delete is restricted to OWNER/ADMIN/MANAGER at the
     * controller level.
     */
    @Transactional
    public void delete(UUID id) {
        UUID businessId = TenantContext.currentBusinessId();
        Expense expense = getOwnedOrThrow(id, businessId);
        expenseRepository.delete(expense);

        auditLogService.record(businessId, TenantContext.currentUserId(), "EXPENSE_DELETED",
                "EXPENSE", id, null, null);
    }

    // -----------------------------------------------------------------
    private Expense getOwnedOrThrow(UUID id, UUID businessId) {
        return expenseRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    private void applyRequest(Expense expense, ExpenseRequest request) {
        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setCategoryId(request.categoryId());
        expense.setReferenceNumber(request.referenceNumber());
        expense.setNotes(request.notes());
        expense.setAttachmentUrl(request.attachmentUrl());
    }
}
