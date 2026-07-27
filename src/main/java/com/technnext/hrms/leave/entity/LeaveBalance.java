package com.technnext.hrms.leave.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "leave_balances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Integer leaveTypeId;

    @Column(nullable = false)
    private Integer year;

    @Builder.Default
    @Column(name = "allocated_days")
    private BigDecimal allocatedDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "used_days")
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "pending_days")
    private BigDecimal pendingDays = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "carried_days")
    private BigDecimal carriedDays = BigDecimal.ZERO;

    // DB-generated: allocated + carried - used - pending. Read-only for Hibernate.
    @Column(name = "balance_days", insertable = false, updatable = false)
    private BigDecimal balanceDays;
}