package com.technnext.hrms.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private String priority = "MEDIUM"; // LOW / MEDIUM / HIGH

    @Builder.Default
    private String status = "OPEN"; // OPEN / IN_PROGRESS / ON_HOLD / CLOSED

    @Column(name = "raised_by_id", nullable = false)
    private UUID raisedById;

    @Column(name = "raised_by_email")
    private String raisedByEmail;

    @Column(name = "resolved_by_id")
    private UUID resolvedById;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}