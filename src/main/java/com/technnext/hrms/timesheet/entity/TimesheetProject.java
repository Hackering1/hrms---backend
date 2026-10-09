package com.technnext.hrms.timesheet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** A project / cost-code that time can be logged against. */
@Entity
@Table(name = "timesheet_projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimesheetProject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}