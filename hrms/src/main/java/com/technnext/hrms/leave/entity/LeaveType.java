package com.technnext.hrms.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_types")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "days_per_year", nullable = false)
    private BigDecimal daysPerYear;

    @Builder.Default
    @Column(name = "is_carry_forward")
    private Boolean isCarryForward = false;

    @Builder.Default
    @Column(name = "max_carry_forward")
    private BigDecimal maxCarryForward = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_paid")
    private Boolean isPaid = true;

    @Builder.Default
    @Column(name = "applicable_gender")
    private String applicableGender = "ALL";

    @Builder.Default
    @Column(name = "requires_document")
    private Boolean requiresDocument = false;

    @Builder.Default
    @Column(name = "min_days_notice")
    private Integer minDaysNotice = 0;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}