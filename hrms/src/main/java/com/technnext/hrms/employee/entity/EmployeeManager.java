package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_managers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Builder.Default
    @Column(name = "is_primary")
    private Boolean isPrimary = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}