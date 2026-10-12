package com.technnext.hrms.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A plan in the company's own plan catalogue (name, price, employee cap, included modules). */
@Entity
@Table(name = "subscription_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "price_monthly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceYearly;

    /** Employee cap; null = unlimited. */
    @Column(name = "max_employees")
    private Integer maxEmployees;

    /** Comma-separated module names this plan includes (informational — not enforced). */
    @Column(length = 1000)
    private String features;

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