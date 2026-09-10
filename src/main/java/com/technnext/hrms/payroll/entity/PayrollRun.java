package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One payroll cycle for the whole company (month/year). DRAFT -> PROCESSED -> APPROVED -> PAID. */
@Entity
@Table(name = "payroll_runs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Builder.Default
    @Column(nullable = false)
    private String status = "DRAFT";

    @Builder.Default
    @Column(name = "total_gross")
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_deductions")
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_net")
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "employee_count")
    private Integer employeeCount = 0;

    @Column(name = "processed_by")
    private UUID processedBy;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private String remarks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}