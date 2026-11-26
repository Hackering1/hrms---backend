package com.technnext.hrms.suite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollProfile {
    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gross;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal basic;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal hra;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lta;
    @Builder.Default
    @Column(name = "dearness_allowance", nullable = false, precision = 14, scale = 2)
    private BigDecimal dearnessAllowance = BigDecimal.ZERO;
    @Column(name = "pf_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal pfRate;
    @Builder.Default
    @Column(name = "pf_contribution_mode", nullable = false, length = 20)
    private String pfContributionMode = "TWELVE_PERCENT";
    @Column(name = "esi_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal esiRate;
    @Column(name = "professional_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal professionalTax;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal tds;
    @Column(name = "employer_pf_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal employerPfRate;
    @Column(name = "employer_esi_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal employerEsiRate;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gratuity;
    @Builder.Default
    @Column(name = "performance_bonus_enabled", nullable = false)
    private Boolean performanceBonusEnabled = false;
    @Builder.Default
    @Column(name = "performance_bonus", nullable = false, precision = 14, scale = 2)
    private BigDecimal performanceBonus = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "group_medical_insurance", nullable = false, precision = 14, scale = 2)
    private BigDecimal groupMedicalInsurance = new BigDecimal("1250.00");
    @Builder.Default
    @Column(name = "tax_regime", nullable = false, length = 8)
    private String taxRegime = "NEW";
    @Builder.Default
    @Column(name = "tax_resident", nullable = false)
    private Boolean taxResident = true;
    @Builder.Default @Column(name = "tax_previous_employer_income", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxPreviousEmployerIncome = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_previous_employer_tds", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxPreviousEmployerTds = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_other_annual_income", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxOtherAnnualIncome = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_rent_paid_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxRentPaidAnnual = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_hra_metro", nullable = false)
    private Boolean taxHraMetro = false;
    @Column(name = "tax_landlord_name", length = 160) private String taxLandlordName;
    @Column(name = "tax_landlord_pan", length = 10) private String taxLandlordPan;
    @Column(name = "tax_landlord_address", length = 500) private String taxLandlordAddress;
    @Builder.Default @Column(name = "tax_lta_claim_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxLtaClaimAnnual = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_home_loan_interest_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxHomeLoanInterestAnnual = BigDecimal.ZERO;
    @Column(name = "tax_lender_name", length = 160) private String taxLenderName;
    @Column(name = "tax_lender_pan", length = 10) private String taxLenderPan;
    @Builder.Default @Column(name = "tax_80c_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal tax80cAnnual = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_80d_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal tax80dAnnual = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_nps_annual", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxNpsAnnual = BigDecimal.ZERO;
    @Column(length = 80)
    private String policyVersion;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate protected void onChange() { updatedAt = LocalDateTime.now(); }
}

