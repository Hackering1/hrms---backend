package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryComponent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    // EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION
    @Column(name = "component_type", nullable = false)
    private String componentType;

    // FLAT | PERCENT_OF_CTC | PERCENT_OF_BASIC | REMAINDER
    @Builder.Default
    @Column(name = "calculation_type", nullable = false)
    private String calculationType = "FLAT";

    @Column(name = "default_percentage")
    private BigDecimal defaultPercentage;

    @Builder.Default
    @Column(name = "is_taxable")
    private Boolean isTaxable = true;

    // System-computed (PF/PT/TDS) — not user-editable per employee structure
    @Builder.Default
    @Column(name = "is_statutory")
    private Boolean isStatutory = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}