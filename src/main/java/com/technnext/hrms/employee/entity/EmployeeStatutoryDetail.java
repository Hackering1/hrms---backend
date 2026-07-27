package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_statutory_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeStatutoryDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "aadhar_number")
    private String aadharNumber;

    @Column(name = "uan_number")
    private String uanNumber;

    @Column(name = "esic_number")
    private String esicNumber;

    @Column(name = "pf_number")
    private String pfNumber;

    @Column(name = "passport_number")
    private String passportNumber;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;
}