package com.technnext.hrms.suite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollProfile {
    @Id
    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gross;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal basic;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal hra;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lta;
    @Column(name = "pf_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal pfRate;
    @Column(name = "esi_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal esiRate;
    @Column(name = "professional_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal professionalTax;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal tds;
    @Column(name = "employer_pf_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal employerPfRate;
    @Column(name = "employer_esi_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal employerEsiRate;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gratuity;
    @Column(length = 80)
    private String policyVersion;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate protected void onChange() { updatedAt = LocalDateTime.now(); }
}
