package com.technnext.hrms.suite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hr_suite_record_archive_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiteRecordArchiveEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        if (actedAt == null) actedAt = LocalDateTime.now();
    }
}

