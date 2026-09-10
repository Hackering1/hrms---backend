package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Tracks which employees have been registered as a Cashfree Payouts beneficiary. */
@Entity
@Table(name = "payout_beneficiaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutBeneficiary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private UUID employeeId;

    @Column(name = "cashfree_beneficiary_id", nullable = false, unique = true)
    private String cashfreeBeneficiaryId;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", nullable = false)
    private String bankIfsc;

    @Builder.Default
    private String status = "VERIFIED";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}