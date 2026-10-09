package com.technnext.hrms.expense.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expense_claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseClaim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String description;

    /** stored_files.id of the uploaded receipt (null when the category doesn't require one). */
    @Column(name = "receipt_file_id")
    private UUID receiptFileId;

    // SUBMITTED -> APPROVED | REJECTED | CANCELLED ; APPROVED -> PAID
    @Builder.Default
    @Column(nullable = false)
    private String status = "SUBMITTED";

    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "reviewer_remarks", length = 1000)
    private String reviewerRemarks;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}