package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An employee's CTC assignment. Supports revisions/increments over time via
 * effective_from / effective_to (effective_to == null means "currently active"),
 * matching the versioning style used elsewhere in this codebase (e.g. LeaveBalance
 * per year, EmployeeManager team history).
 */
@Entity
@Table(name = "employee_salaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeSalary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "salary_structure_id", nullable = false)
    private Integer salaryStructureId;

    @Column(name = "annual_ctc", nullable = false)
    private BigDecimal annualCtc;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    // NEW | OLD — OLD regime declarations/proofs are not computed yet (see
    // PayrollCalculationService); an OLD-regime employee's TDS must currently
    // be set via tdsOverrideMonthly.
    @Builder.Default
    @Column(name = "tax_regime")
    private String taxRegime = "NEW";

    @Builder.Default
    @Column(name = "pf_applicable")
    private Boolean pfApplicable = true;

    @Builder.Default
    @Column(name = "pt_applicable")
    private Boolean ptApplicable = true;

    @Column(name = "tds_override_monthly")
    private BigDecimal tdsOverrideMonthly;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}