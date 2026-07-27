package com.technnext.hrms.organization.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "designations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "department_id")
    private Integer departmentId;

    private Integer level;   // 1 = junior ... 5 = senior

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}