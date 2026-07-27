package com.technnext.hrms.notification.dto;

import java.util.UUID;

public record NotificationCreate(
        UUID userId,
        String title,
        String message,
        String type,
        String module,
        String referenceId
) {}