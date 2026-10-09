package com.technnext.hrms.expense.repository;

import com.technnext.hrms.expense.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Integer> {
    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<ExpenseClaim> findByStatusOrderByCreatedAtAsc(String status);
    /** Used by FileAccessService so an approver can open the receipt on a claim they may review. */
    List<ExpenseClaim> findByReceiptFileId(UUID receiptFileId);
    boolean existsByEmployeeIdAndCategoryIdAndExpenseDateAndAmountAndStatusIn(
            UUID employeeId, Integer categoryId, LocalDate expenseDate, BigDecimal amount, Collection<String> statuses);
}