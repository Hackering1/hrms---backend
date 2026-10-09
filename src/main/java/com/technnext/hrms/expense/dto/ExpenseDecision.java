package com.technnext.hrms.expense.dto;

/** status: APPROVED or REJECTED. Remarks are mandatory when rejecting. */
public record ExpenseDecision(String status, String remarks) {}