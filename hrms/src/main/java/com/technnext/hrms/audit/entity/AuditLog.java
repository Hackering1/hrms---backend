package com.technnext.hrms.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps to the existing audit_logs table. Records an override/important action:
 * who (userId), what (action + module + recordId), and the before/after values.
 *
 * old_values / new_values are jsonb in the DB. We store JSON as a String, but
 * bind it to the column with @JdbcTypeCode(SqlTypes.JSON) so Hibernate sends a
 * real jsonb value instead of a varchar (Postgres rejects a plain varchar into
 * a jsonb column without a cast). The String passed in MUST be valid JSON.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action", nullable = false)
    private String action;   // e.g. "LEAVE_CANCELLED", "LEAVE_EDITED"

    @Column(name = "module", nullable = false)
    private String module;   // e.g. "LEAVE"

    @Column(name = "record_id")
    private String recordId; // e.g. the leave request id as a string

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues; // JSON string, may be null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues; // JSON string, may be null

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}