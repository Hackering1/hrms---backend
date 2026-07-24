package com.technnext.hrms.auth.repository;

import com.technnext.hrms.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByToken(String token);

    @Transactional
    void deleteByToken(String token);

    @Transactional
    void deleteByUserId(UUID userId);
}