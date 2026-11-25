package com.technnext.hrms.suite.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollProfileRequest(
        @NotNull UUID employeeId,
        @NotNull @DecimalMin("0.00") BigDecimal gross,
        @NotNull @DecimalMin("0.00") BigDecimal basic,
        @NotNull @DecimalMin("0.00") BigDecimal hra,
        @NotNull @DecimalMin("0.00") BigDecimal lta,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal pfRate,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal esiRate,
        @NotNull @DecimalMin("0.00") BigDecimal professionalTax,
        @NotNull @DecimalMin("0.00") BigDecimal tds,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal employerPfRate,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal employerEsiRate,
        @NotNull @DecimalMin("0.00") BigDecimal gratuity,
        @Size(max = 80) String policyVersion) { }
