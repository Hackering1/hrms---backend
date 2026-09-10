package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Constants around the tax slabs for a financial year — standard deduction, Section 87A rebate, cess. */
@Entity
@Table(name = "tax_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "financial_year", nullable = false, unique = true)
    private String financialYear;

    @Builder.Default
    @Column(name = "standard_deduction")
    private BigDecimal standardDeduction = new BigDecimal("75000");

    @Builder.Default
    @Column(name = "rebate_87a_income_limit")
    private BigDecimal rebate87aIncomeLimit = new BigDecimal("1200000");

    @Builder.Default
    @Column(name = "rebate_87a_amount")
    private BigDecimal rebate87aAmount = new BigDecimal("60000");

    @Builder.Default
    @Column(name = "cess_percent")
    private BigDecimal cessPercent = new BigDecimal("4.00");

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}