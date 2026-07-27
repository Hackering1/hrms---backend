package com.technnext.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "employee_contacts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private UUID employeeId;

    @Column(name = "personal_email")
    private String personalEmail;

    @Column(name = "official_email")
    private String officialEmail;

    @Column(name = "phone_primary")
    private String phonePrimary;

    @Column(name = "phone_secondary")
    private String phoneSecondary;

    @Column(name = "emergency_name")
    private String emergencyName;

    @Column(name = "emergency_phone")
    private String emergencyPhone;

    @Column(name = "emergency_relation")
    private String emergencyRelation;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String state;
    private String country;
    private String pincode;

    @Column(name = "perm_address_line1")
    private String permAddressLine1;

    @Column(name = "perm_address_line2")
    private String permAddressLine2;

    @Column(name = "perm_city")
    private String permCity;

    @Column(name = "perm_state")
    private String permState;

    @Column(name = "perm_pincode")
    private String permPincode;
}