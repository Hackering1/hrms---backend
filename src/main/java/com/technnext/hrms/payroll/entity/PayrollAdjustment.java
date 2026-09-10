package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One-off addition/deduction for a specific employee in a specific month (bonus, loan recovery, reimbursement). */
@Entity
@Table(name = "payroll_adjustments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    // EARNING | DEDUCTION
    @Column(name = "adjustment_type", nullable = false)
    private String adjustmentType;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "is_taxable")
    private Boolean isTaxable = true;

    private String remarks;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Once a run consumes this adjustment, it's locked against further edits.
    @Column(name = "applied_in_run_id")
    private Integer appliedInRunId;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}