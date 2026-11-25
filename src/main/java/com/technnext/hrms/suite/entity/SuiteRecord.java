package com.technnext.hrms.suite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Shared record envelope for the new HR suite workstreams. Employee ownership is
 * always resolved and checked in SuiteController; clients never control creator. */
@Entity
@Table(name = "hr_suite_records", indexes = {
        @Index(name = "idx_suite_module_employee", columnList = "module_key, employee_id"),
        @Index(name = "idx_suite_module_period", columnList = "module_key, period_key")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SuiteRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "module_key", nullable = false, length = 32)
    private String module;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Transient
    private String employeeLabel;

    @Builder.Default
    @Column(nullable = false, length = 24)
    private String status = "OPEN";

    @Column(name = "period_key", length = 16)
    private String period;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    private Integer minutes;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String details;

    @Builder.Default
    @Column(nullable = false)
    private Boolean consent = false;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
