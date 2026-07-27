package com.technnext.hrms.hrops.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exit_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExitRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "resignation_date")
    private LocalDate resignationDate;

    @Column(name = "last_working_date")
    private LocalDate lastWorkingDate;

    @Column(name = "exit_type")
    private String exitType;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Builder.Default
    @Column(name = "notice_waived")
    private Boolean noticeWaived = false;

    @Builder.Default
    @Column(name = "exit_interview_done")
    private Boolean exitInterviewDone = false;

    @Column(name = "exit_remarks")
    private String exitRemarks;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}