package com.technnext.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "permissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String module;

    @Column(nullable = false)
    private String action;      // CREATE, READ, UPDATE, DELETE

    private String description;

    /** Authority string used by Spring Security, e.g. "EMPLOYEE:READ". */
    public String authority() {
        return module + ":" + action;
    }
}