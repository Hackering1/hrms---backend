package com.technnext.hrms.payroll.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeeSalaryAssignRequest(
        @NotNull UUID employeeId,
        @NotNull Integer salaryStructureId,
        @NotNull BigDecimal annualCtc,
        @NotNull LocalDate effectiveFrom,
        String taxRegime,                  // NEW | OLD, defaults to NEW
        Boolean pfApplicable,
        Boolean ptApplicable,
        BigDecimal tdsOverrideMonthly,
        String bankAccountNumber
) {}