package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employee_education")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeEducation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String level;

    private String institution;
    private String specialization;
    private String percentage;

    @Column(name = "from_month") private Integer fromMonth;
    @Column(name = "from_year")  private Integer fromYear;
    @Column(name = "to_month")   private Integer toMonth;
    @Column(name = "to_year")    private Integer toYear;

    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}