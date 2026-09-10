package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payslips")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payslip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payroll_run_id", nullable = false)
    private Integer payrollRunId;

    // Denormalized from the PayrollRun at creation time (a payslip's month/year never
    // changes independently of its run) — avoids a cross-entity JOIN for the common
    // "TDS paid so far this financial year" query.
    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "employee_salary_id")
    private Integer employeeSalaryId;

    @Column(name = "working_days", nullable = false)
    private BigDecimal workingDays;

    @Column(name = "paid_days", nullable = false)
    private BigDecimal paidDays;

    @Builder.Default
    @Column(name = "lop_days")
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "gross_earnings", nullable = false)
    private BigDecimal grossEarnings;

    @Column(name = "total_deductions", nullable = false)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false)
    private BigDecimal netPay;

    @Builder.Default
    @Column(name = "employer_cost")
    private BigDecimal employerCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pf_employee")
    private BigDecimal pfEmployee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pf_employer")
    private BigDecimal pfEmployer = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pt_amount")
    private BigDecimal ptAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tds_amount")
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Builder.Default
    private String status = "GENERATED"; // GENERATED | PAID | HELD

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}