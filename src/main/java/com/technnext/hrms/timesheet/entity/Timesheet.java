package com.technnext.hrms.timesheet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One employee's timesheet for one ISO week (week_start is always a Monday). */
@Entity
@Table(name = "timesheets",
        uniqueConstraints = @UniqueConstraint(name = "uq_timesheet_employee_week", columnNames = {"employee_id", "week_start"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Timesheet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    // DRAFT -> SUBMITTED -> APPROVED | REJECTED (a REJECTED sheet can be edited and re-submitted)
    @Builder.Default
    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // references employees(id); null when the reviewer is a profile-less admin account
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_remarks", length = 1000)
    private String reviewerRemarks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}