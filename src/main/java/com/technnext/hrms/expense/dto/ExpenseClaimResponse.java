package com.technnext.hrms.expense.dto;

import com.technnext.hrms.expense.entity.ExpenseClaim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseClaimResponse(
        Integer id,
        UUID employeeId,
        Integer categoryId,
        LocalDate expenseDate,
        BigDecimal amount,
        String description,
        UUID receiptFileId,
        String receiptUrl,
        String status,
        UUID reviewedBy,
        LocalDateTime reviewedAt,
        String reviewerRemarks,
        LocalDateTime paidAt,
        String paymentReference,
        LocalDateTime createdAt
) {
    public static ExpenseClaimResponse from(ExpenseClaim c) {
        return new ExpenseClaimResponse(c.getId(), c.getEmployeeId(), c.getCategoryId(), c.getExpenseDate(),
                c.getAmount(), c.getDescription(), c.getReceiptFileId(),
                c.getReceiptFileId() == null ? null : "/api/files/" + c.getReceiptFileId(),
                c.getStatus(), c.getReviewedBy(), c.getReviewedAt(), c.getReviewerRemarks(),
                c.getPaidAt(), c.getPaymentReference(), c.getCreatedAt());
    }
}