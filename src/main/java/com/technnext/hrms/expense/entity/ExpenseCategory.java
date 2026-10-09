package com.technnext.hrms.expense.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpenseCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Largest amount a single claim in this category may be for; null = no limit. */
    @Column(name = "max_amount", precision = 12, scale = 2)
    private BigDecimal maxAmount;

    @Builder.Default
    @Column(name = "requires_receipt", nullable = false)
    private Boolean requiresReceipt = true;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}