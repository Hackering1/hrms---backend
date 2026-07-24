package com.technnext.hrms.organization.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "branches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    private String address;
    private String city;
    private String state;

    @Builder.Default
    private String country = "India";

    private String pincode;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}