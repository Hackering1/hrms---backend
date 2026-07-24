package com.technnext.hrms.user.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        List<String> roles,
        Boolean isActive,
        LocalDateTime lastLogin
) {}