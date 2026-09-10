package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "salary_structure_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryStructureComponent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "salary_structure_id", nullable = false)
    private Integer salaryStructureId;

    @Column(name = "salary_component_id", nullable = false)
    private Integer salaryComponentId;

    // Overrides the component's own default for THIS template.
    @Column(name = "calculation_type", nullable = false)
    private String calculationType;

    private BigDecimal percentage;

    @Column(name = "flat_amount")
    private BigDecimal flatAmount;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;
}