package com.technnext.hrms.plan.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Which plan the company is on. At most one row is ACTIVE at a time; older rows are history. */
@Entity
@Table(name = "company_subscriptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanySubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "plan_id", nullable = false)
    private Integer planId;

    // MONTHLY | YEARLY
    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Renewal / expiry date; set automatically from the billing cycle. */
    @Column(name = "end_date")
    private LocalDate endDate;

    // ACTIVE | CANCELLED | REPLACED
    @Builder.Default
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}