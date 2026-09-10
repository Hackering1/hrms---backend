package com.technnext.hrms.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PayrollRunRequest(
        @NotNull @Min(1) @Max(12) Integer month,
        @NotNull Integer year
) {}