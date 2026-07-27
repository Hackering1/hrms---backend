package com.technnext.hrms.letter.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_letters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedLetter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "template_id")
    private Integer templateId;

    @Column(name = "letter_type", nullable = false)
    private String letterType;

    @Column(name = "letter_date", nullable = false)
    private LocalDate letterDate;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}