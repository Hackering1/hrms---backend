package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_experience")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeExperience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String company;

    private String designation;

    @Column(name = "from_month") private Integer fromMonth;
    @Column(name = "from_year")  private Integer fromYear;
    @Column(name = "to_month")   private Integer toMonth;
    @Column(name = "to_year")    private Integer toYear;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}