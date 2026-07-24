package com.technnext.hrms.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}