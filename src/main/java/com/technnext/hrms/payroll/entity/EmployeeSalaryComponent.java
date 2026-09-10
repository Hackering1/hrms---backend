package com.technnext.hrms.payroll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** The computed per-component breakup (monthly + annual) for one EmployeeSalary assignment. */
@Entity
@Table(name = "employee_salary_components")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeSalaryComponent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_salary_id", nullable = false)
    private Integer employeeSalaryId;

    @Column(name = "salary_component_id", nullable = false)
    private Integer salaryComponentId;

    @Column(name = "monthly_amount", nullable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "annual_amount", nullable = false)
    private BigDecimal annualAmount;
}