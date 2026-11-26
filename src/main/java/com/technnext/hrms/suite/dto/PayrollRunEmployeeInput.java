package com.technnext.hrms.suite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PayrollRunEmployeeInput(
        @NotNull UUID employeeId,
        @NotNull @Min(0) @Max(31) Integer paidDays,
        @NotNull @Min(0) @Max(31) Integer lopDays) { }
