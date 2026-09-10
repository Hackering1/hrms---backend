package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** One income-tax slab band for a financial year + regime. from/to are ANNUAL taxable-income amounts. */
@Entity
@Table(name = "tax_slabs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxSlab {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "financial_year", nullable = false)
    private String financialYear; // e.g. "2026-27"

    @Builder.Default
    @Column(nullable = false)
    private String regime = "NEW";

    @Column(name = "from_amount", nullable = false)
    private BigDecimal fromAmount;

    @Column(name = "to_amount")
    private BigDecimal toAmount; // null = no upper bound

    @Column(name = "rate_percent", nullable = false)
    private BigDecimal ratePercent;
}