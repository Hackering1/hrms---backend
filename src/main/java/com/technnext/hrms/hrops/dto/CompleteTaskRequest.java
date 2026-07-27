package com.technnext.hrms.hrops.dto;

import java.util.UUID;

public record CompleteTaskRequest(
        boolean completed,
        UUID completedBy
) {}