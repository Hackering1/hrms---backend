package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One payout attempt (Cashfree batch transfer) for a single payroll run. One row per payroll_run — see UNIQUE constraint in the migration. */
@Entity
@Table(name = "payout_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payroll_run_id", nullable = false, unique = true)
    private Integer payrollRunId;

    @Column(name = "batch_transfer_id", nullable = false, unique = true)
    private String batchTransferId;

    @Column(name = "cf_batch_transfer_id")
    private String cfBatchTransferId;

    @Builder.Default
    private String status = "INITIATED"; // INITIATED | RECEIVED | PROCESSING | COMPLETED | PARTIALLY_FAILED | FAILED

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "employee_count", nullable = false)
    private Integer employeeCount;

    @Column(nullable = false)
    private String environment; // TEST | PROD

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "initiated_at", updatable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "last_status_check_at")
    private LocalDateTime lastStatusCheckAt;

    @Column(name = "error_message")
    private String errorMessage;

    @PrePersist protected void onCreate() { initiatedAt = LocalDateTime.now(); }
}