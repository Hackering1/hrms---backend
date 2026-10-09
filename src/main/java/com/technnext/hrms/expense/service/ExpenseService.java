package com.technnext.hrms.expense.service;

import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.expense.dto.ExpenseCategoryRequest;
import com.technnext.hrms.expense.dto.ExpenseClaimCreate;
import com.technnext.hrms.expense.dto.ExpenseClaimResponse;
import com.technnext.hrms.expense.dto.ExpenseDecision;
import com.technnext.hrms.expense.dto.ExpensePayment;
import com.technnext.hrms.expense.entity.ExpenseCategory;
import com.technnext.hrms.expense.entity.ExpenseClaim;
import com.technnext.hrms.expense.repository.ExpenseCategoryRepository;
import com.technnext.hrms.expense.repository.ExpenseClaimRepository;
import com.technnext.hrms.file.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Expense claims: an employee submits a claim (optionally with a receipt), a manager / HR
 * approves or rejects it, and HR then records it as PAID. Payment is recorded here only; it is
 * deliberately NOT wired into payroll runs so payroll calculations stay untouched.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final BigDecimal MAX_CLAIM = new BigDecimal("10000000");
    private static final int MAX_AGE_DAYS = 365;

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseClaimRepository claimRepository;
    private final StoredFileRepository storedFileRepository;

    // ── Categories ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExpenseCategory> listCategories(boolean activeOnly) {
        return activeOnly ? categoryRepository.findByIsActiveTrueOrderByNameAsc()
                          : categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public ExpenseCategory createCategory(ExpenseCategoryRequest req) {
        String name = clean(req.name());
        if (name == null) throw new BadRequestException("Category name is required.");
        if (name.length() > 100) throw new BadRequestException("Category name is too long (max 100).");
        validateMax(req.maxAmount());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A category named '" + name + "' already exists.");
        }
        return categoryRepository.save(ExpenseCategory.builder()
                .name(name)
                .maxAmount(req.maxAmount())
                .requiresReceipt(req.requiresReceipt() == null ? true : req.requiresReceipt())
                .isActive(req.isActive() == null ? true : req.isActive())
                .build());
    }

    @Transactional
    public ExpenseCategory updateCategory(Integer id, ExpenseCategoryRequest req) {
        ExpenseCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category", id));
        String name = clean(req.name());
        if (name == null) throw new BadRequestException("Category name is required.");
        if (name.length() > 100) throw new BadRequestException("Category name is too long (max 100).");
        validateMax(req.maxAmount());
        if (!name.equalsIgnoreCase(c.getName()) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A category named '" + name + "' already exists.");
        }
        c.setName(name);
        c.setMaxAmount(req.maxAmount());
        if (req.requiresReceipt() != null) c.setRequiresReceipt(req.requiresReceipt());
        if (req.isActive() != null) c.setIsActive(req.isActive());
        return categoryRepository.save(c);
    }

    // ── Claims ──────────────────────────────────────────────────────────────────

    /**
     * @param employeeId the claimant (always the caller's own employee id)
     * @param userId     the caller's USER id — a receipt must have been uploaded by this user
     */
    @Transactional
    public ExpenseClaimResponse submit(UUID employeeId, UUID userId, ExpenseClaimCreate req) {
        if (req == null || req.categoryId() == null) throw new BadRequestException("Choose an expense category.");
        ExpenseCategory cat = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new BadRequestException("Unknown expense category."));
        if (!Boolean.TRUE.equals(cat.getIsActive())) {
            throw new BadRequestException("Category '" + cat.getName() + "' is inactive and can't be used.");
        }
        if (req.expenseDate() == null) throw new BadRequestException("Expense date is required.");
        LocalDate today = LocalDate.now();
        if (req.expenseDate().isAfter(today)) throw new BadRequestException("Expense date can't be in the future.");
        if (req.expenseDate().isBefore(today.minusDays(MAX_AGE_DAYS))) {
            throw new BadRequestException("Expenses older than " + MAX_AGE_DAYS + " days can't be claimed.");
        }
        if (req.amount() == null || req.amount().signum() <= 0) throw new BadRequestException("Amount must be greater than 0.");
        BigDecimal amount = req.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MAX_CLAIM) > 0) throw new BadRequestException("Amount is too large.");
        if (cat.getMaxAmount() != null && amount.compareTo(cat.getMaxAmount()) > 0) {
            throw new BadRequestException("The limit for '" + cat.getName() + "' is Rs." + cat.getMaxAmount()
                    + " per claim. Split it or contact HR.");
        }
        String description = clean(req.description());
        if (description == null) throw new BadRequestException("Please describe the expense.");
        if (description.length() > 1000) throw new BadRequestException("Description is too long (max 1000 characters).");

        if (req.receiptFileId() == null) {
            if (Boolean.TRUE.equals(cat.getRequiresReceipt())) {
                throw new BadRequestException("A receipt is required for '" + cat.getName() + "' claims.");
            }
        } else if (storedFileRepository.countByIdAndUploadedBy(req.receiptFileId(), userId) == 0) {
            // Only a file the caller uploaded themselves may be attached — otherwise a claim could
            // point at (and thereby expose) someone else's stored file.
            throw new BadRequestException("The receipt must be a file you uploaded yourself.");
        }

        if (claimRepository.existsByEmployeeIdAndCategoryIdAndExpenseDateAndAmountAndStatusIn(
                employeeId, cat.getId(), req.expenseDate(), amount, List.of("SUBMITTED", "APPROVED", "PAID"))) {
            throw new BadRequestException("You already have a claim for the same category, date and amount.");
        }

        ExpenseClaim claim = claimRepository.save(ExpenseClaim.builder()
                .employeeId(employeeId)
                .categoryId(cat.getId())
                .expenseDate(req.expenseDate())
                .amount(amount)
                .description(description)
                .receiptFileId(req.receiptFileId())
                .status("SUBMITTED")
                .build());
        return ExpenseClaimResponse.from(claim);
    }

    @Transactional(readOnly = true)
    public List<ExpenseClaimResponse> myClaims(UUID employeeId) {
        return claimRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(ExpenseClaimResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseClaimResponse> byStatus(String status) {
        return claimRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(ExpenseClaimResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseClaimResponse getById(Integer id) {
        return ExpenseClaimResponse.from(find(id));
    }

    @Transactional
    public ExpenseClaimResponse cancel(Integer id, UUID employeeId) {
        ExpenseClaim c = find(id);
        if (!c.getEmployeeId().equals(employeeId)) {
            throw new BadRequestException("You can only cancel your own claims.");
        }
        if (!"SUBMITTED".equals(c.getStatus())) {
            throw new BadRequestException("Only a claim that is still awaiting review can be cancelled (this one is " + c.getStatus() + ").");
        }
        c.setStatus("CANCELLED");
        return ExpenseClaimResponse.from(claimRepository.save(c));
    }

    /** @param reviewerEmployeeId the reviewer's employee id, or null for a profile-less admin account */
    @Transactional
    public ExpenseClaimResponse decide(Integer id, UUID reviewerEmployeeId, ExpenseDecision d) {
        ExpenseClaim c = find(id);
        String status = d == null || d.status() == null ? "" : d.status().trim().toUpperCase();
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BadRequestException("Decision must be APPROVED or REJECTED.");
        }
        if (!"SUBMITTED".equals(c.getStatus())) {
            throw new BadRequestException("Only a submitted claim can be reviewed (this one is " + c.getStatus() + ").");
        }
        if (reviewerEmployeeId != null && reviewerEmployeeId.equals(c.getEmployeeId())) {
            throw new BadRequestException("You can't review your own expense claim.");
        }
        String remarks = clean(d.remarks());
        if ("REJECTED".equals(status) && remarks == null) {
            throw new BadRequestException("Please give a reason when rejecting a claim.");
        }
        if (remarks != null && remarks.length() > 1000) throw new BadRequestException("Remarks are too long (max 1000 characters).");
        c.setStatus(status);
        c.setReviewedBy(reviewerEmployeeId);
        c.setReviewedAt(LocalDateTime.now());
        c.setReviewerRemarks(remarks);
        return ExpenseClaimResponse.from(claimRepository.save(c));
    }

    @Transactional
    public ExpenseClaimResponse markPaid(Integer id, ExpensePayment p) {
        ExpenseClaim c = find(id);
        if (!"APPROVED".equals(c.getStatus())) {
            throw new BadRequestException("Only an approved claim can be marked as paid (this one is " + c.getStatus() + ").");
        }
        String ref = p == null ? null : clean(p.reference());
        if (ref != null && ref.length() > 100) throw new BadRequestException("Payment reference is too long (max 100).");
        c.setStatus("PAID");
        c.setPaidAt(LocalDateTime.now());
        c.setPaymentReference(ref);
        return ExpenseClaimResponse.from(claimRepository.save(c));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private ExpenseClaim find(Integer id) {
        return claimRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Expense claim", id));
    }

    private void validateMax(BigDecimal max) {
        if (max != null && max.signum() <= 0) throw new BadRequestException("Maximum amount must be greater than 0 (or empty for no limit).");
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}