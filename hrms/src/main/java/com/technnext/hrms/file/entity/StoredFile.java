package com.technnext.hrms.file.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_storage")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoredFile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    // Stored directly in the database as bytea.
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}