package com.technnext.hrms.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseClaimCreate(Integer categoryId, LocalDate expenseDate, BigDecimal amount,
                                 String description, UUID receiptFileId) {}