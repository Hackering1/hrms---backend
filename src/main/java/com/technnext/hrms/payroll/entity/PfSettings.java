package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Provident Fund statutory config. Normally a single active row; kept as a table (not hardcoded constants) so Finance can adjust the EPFO wage ceiling without a code deploy. */
@Entity
@Table(name = "pf_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PfSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Builder.Default
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Builder.Default
    @Column(name = "employee_rate_percent")
    private BigDecimal employeeRatePercent = new BigDecimal("12.00");

    @Builder.Default
    @Column(name = "employer_rate_percent")
    private BigDecimal employerRatePercent = new BigDecimal("12.00");

    @Builder.Default
    @Column(name = "eps_rate_percent")
    private BigDecimal epsRatePercent = new BigDecimal("8.33");

    @Builder.Default
    @Column(name = "wage_ceiling")
    private BigDecimal wageCeiling = new BigDecimal("15000.00");

    @Builder.Default
    @Column(name = "eps_wage_ceiling")
    private BigDecimal epsWageCeiling = new BigDecimal("15000.00");

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}