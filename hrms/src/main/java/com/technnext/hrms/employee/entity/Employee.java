package com.technnext.hrms.employee.entity;

import com.technnext.hrms.organization.entity.Branch;
import com.technnext.hrms.organization.entity.Department;
import com.technnext.hrms.organization.entity.Designation;
import com.technnext.hrms.organization.entity.Shift;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;
    @Column(name = "first_name", nullable = false)
    private String firstName;
    @Column(name = "last_name", nullable = false)
    private String lastName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    private String gender;
    @Column(name = "blood_group")
    private String bloodGroup;
    @Column(name = "marital_status")
    private String maritalStatus;
    private String nationality;
    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;
    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;
    @Builder.Default @Column(name = "employment_type")
    private String employmentType = "FULL_TIME";
    @Builder.Default
    private String status = "ACTIVE";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id")
    private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id")
    private Department department;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "designation_id")
    private Designation designation;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "shift_id")
    private Shift shift;
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;
    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { this.createdAt = LocalDateTime.now(); this.updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
 // --- Personal documents + fresher flag ---
    @Builder.Default
    @Column(name = "is_fresher")
    private Boolean isFresher = true;
    @Column(name = "aadhaar_number") private String aadhaarNumber;
    @Column(name = "pan_number") private String panNumber;
    @Column(name = "bank_account_number") private String bankAccountNumber;
    @Column(name = "bank_name") private String bankName;
    @Column(name = "ifsc_code") private String ifscCode;
    @Column(name = "uan_number") private String uanNumber;
    @Column(name = "email") private String email;
}