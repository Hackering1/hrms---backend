package com.technnext.hrms.suite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Tombstone audit record retained after a payroll and payslip are permanently deleted. */
@Entity
@Table(name = "hr_suite_record_deletion_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollDeletionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payroll_record_id", nullable = false)
    private UUID payrollRecordId;

    @Column(name = "payslip_record_id")
    private UUID payslipRecordId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "payroll_status", nullable = false, length = 24)
    private String payrollStatus;

    @Column(name = "payslip_status", length = 24)
    private String payslipStatus;

    @Column(name = "payroll_amount", precision = 14, scale = 2)
    private BigDecimal payrollAmount;

    @Column(name = "payslip_amount", precision = 14, scale = 2)
    private BigDecimal payslipAmount;

    @Column(name = "deleted_by", nullable = false)
    private UUID deletedBy;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (deletedAt == null) deletedAt = LocalDateTime.now();
    }
}

