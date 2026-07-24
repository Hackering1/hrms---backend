package com.technnext.hrms.auth.dto;

import java.util.List;

public record AuthResponse(
        String userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        String email,
        List<String> roles,
        boolean mustChangePassword
) {}