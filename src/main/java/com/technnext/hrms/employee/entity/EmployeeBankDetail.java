package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "employee_bank_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeBankDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "account_type")
    private String accountType;

    @Column(name = "branch_name")
    private String branchName;

    @Builder.Default
    @Column(name = "is_primary")
    private Boolean isPrimary = true;
}