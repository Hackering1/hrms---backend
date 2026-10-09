package com.technnext.hrms.expense.dto;

import java.math.BigDecimal;

public record ExpenseCategoryRequest(String name, BigDecimal maxAmount, Boolean requiresReceipt, Boolean isActive) {}