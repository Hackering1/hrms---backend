package com.technnext.hrms.expense.dto;

/** Optional bank / UPI / payroll reference recorded when an approved claim is paid. */
public record ExpensePayment(String reference) {}