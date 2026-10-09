package com.technnext.hrms.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** One employee's review for one cycle: a self review, then the manager's review. */
@Entity
@Table(name = "performance_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uq_review_cycle_employee", columnNames = {"cycle_id", "employee_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cycle_id", nullable = false)
    private Integer cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    // SELF_SUBMITTED -> COMPLETED
    @Builder.Default
    @Column(nullable = false)
    private String status = "SELF_SUBMITTED";

    @Column(name = "self_rating", nullable = false)
    private Integer selfRating;

    @Column(name = "self_comments", nullable = false, length = 2000)
    private String selfComments;

    @Column(name = "self_submitted_at")
    private LocalDateTime selfSubmittedAt;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_comments", length = 2000)
    private String managerComments;

    // employees(id) of the reviewer; null for a profile-less admin account
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}