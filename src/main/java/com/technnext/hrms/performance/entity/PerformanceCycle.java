package com.technnext.hrms.performance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A review period (e.g. "FY 2026-27 H1"). Goals and reviews hang off a cycle. */
@Entity
@Table(name = "performance_cycles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceCycle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // OPEN | CLOSED — a CLOSED cycle is read-only
    @Builder.Default
    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}