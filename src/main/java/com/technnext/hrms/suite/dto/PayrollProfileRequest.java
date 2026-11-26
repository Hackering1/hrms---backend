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
        Boolean performanceBonusEnabled,
        @DecimalMin("0.00") BigDecimal performanceBonus,
        @Size(max = 80) String policyVersion,
        @DecimalMin("0.00") BigDecimal dearnessAllowance,
        @Pattern(regexp = "TWELVE_PERCENT|FLAT_3000") String pfContributionMode,
        @DecimalMin("0.00") BigDecimal groupMedicalInsurance,
        @Pattern(regexp = "NEW|OLD") String taxRegime,
        Boolean taxResident,
        @DecimalMin("0.00") BigDecimal taxPreviousEmployerIncome,
        @DecimalMin("0.00") BigDecimal taxPreviousEmployerTds,
        @DecimalMin("0.00") BigDecimal taxOtherAnnualIncome,
        @DecimalMin("0.00") BigDecimal taxRentPaidAnnual,
        Boolean taxHraMetro,
        @Size(max = 160) String taxLandlordName,
        @Pattern(regexp = "^$|[A-Z]{5}[0-9]{4}[A-Z]$") String taxLandlordPan,
        @Size(max = 500) String taxLandlordAddress,
        @DecimalMin("0.00") BigDecimal taxLtaClaimAnnual,
        @DecimalMin("0.00") BigDecimal taxHomeLoanInterestAnnual,
        @Size(max = 160) String taxLenderName,
        @Pattern(regexp = "^$|[A-Z]{5}[0-9]{4}[A-Z]$") String taxLenderPan,
        @DecimalMin("0.00") BigDecimal tax80cAnnual,
        @DecimalMin("0.00") BigDecimal tax80dAnnual,
        @DecimalMin("0.00") BigDecimal taxNpsAnnual) { }

