package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Professional Tax slab row for a state (+ optional gender split — Maharashtra differs by gender). */
@Entity
@Table(name = "pt_slabs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PtSlab {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String state;

    @Builder.Default
    @Column(nullable = false)
    private String gender = "ALL"; // ALL | MALE | FEMALE

    @Column(name = "min_salary", nullable = false)
    private BigDecimal minSalary;

    @Column(name = "max_salary")
    private BigDecimal maxSalary; // null = no upper bound

    @Column(name = "monthly_amount", nullable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "february_amount")
    private BigDecimal februaryAmount; // null = same as monthlyAmount

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}