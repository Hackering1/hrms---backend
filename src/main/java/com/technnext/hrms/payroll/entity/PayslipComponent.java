package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** Frozen line-item snapshot on a payslip — kept even if the salary structure changes later. */
@Entity
@Table(name = "payslip_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayslipComponent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "payslip_id", nullable = false)
    private Integer payslipId;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    // EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION
    @Column(name = "component_type", nullable = false)
    private String componentType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;
}