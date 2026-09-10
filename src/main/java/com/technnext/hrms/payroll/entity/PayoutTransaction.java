package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One employee's individual transfer within a PayoutBatch. */
@Entity
@Table(name = "payout_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payout_batch_id", nullable = false)
    private Integer payoutBatchId;

    @Column(name = "payslip_id", nullable = false)
    private Integer payslipId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "transfer_id", nullable = false, unique = true)
    private String transferId;

    @Column(name = "cf_transfer_id")
    private String cfTransferId;

    @Column(name = "beneficiary_id", nullable = false)
    private String beneficiaryId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Builder.Default
    private String status = "RECEIVED"; // RECEIVED | PENDING | SUCCESS | FAILED | REVERSED

    @Column(name = "status_description")
    private String statusDescription;

    private String utr;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate protected void onSave() { updatedAt = LocalDateTime.now(); }
}