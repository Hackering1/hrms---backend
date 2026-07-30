package com.technnext.hrms.invite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per "Send Invitation" click. Tracks the secure onboarding token for
 * an employee shell created by Super Admin via the Invite Employee flow.
 *
 * Status lifecycle: PENDING -> USED (candidate completed onboarding)
 *                    PENDING -> (treated as EXPIRED once expiresAt has passed —
 *                                checked dynamically, no scheduled job needed)
 *                    PENDING -> CANCELLED (admin cancelled it before use)
 */
@Entity
@Table(name = "employee_invites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "login_role", nullable = false)
    private String loginRole;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING"; // PENDING | USED | CANCELLED

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    @Transient
    public boolean isExpired() {
        return "PENDING".equals(status) && expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}