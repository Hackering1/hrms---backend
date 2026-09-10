package com.technnext.hrms.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollAdjustmentRequest(
        @NotNull UUID employeeId,
        @NotNull Integer month,
        @NotNull Integer year,
        @NotBlank String adjustmentType, // EARNING | DEDUCTION
        @NotBlank String label,
        @NotNull BigDecimal amount,
        Boolean isTaxable,
        String remarks
) {}