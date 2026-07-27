package com.technnext.hrms.hrops.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "confirmation_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfirmationRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "confirmation_date", nullable = false)
    private LocalDate confirmationDate;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    private String remarks;

    @Builder.Default
    @Column(name = "letter_generated")
    private Boolean letterGenerated = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}