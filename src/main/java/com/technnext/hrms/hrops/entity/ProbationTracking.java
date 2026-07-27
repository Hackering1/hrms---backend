package com.technnext.hrms.hrops.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "probation_tracking")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProbationTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "probation_start", nullable = false)
    private LocalDate probationStart;

    @Column(name = "probation_end", nullable = false)
    private LocalDate probationEnd;

    @Column(name = "extended_end_date")
    private LocalDate extendedEndDate;

    @Builder.Default
    private String status = "IN_PROGRESS";

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}