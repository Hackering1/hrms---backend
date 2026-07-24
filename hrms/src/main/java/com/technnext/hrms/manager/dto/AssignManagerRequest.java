package com.technnext.hrms.manager.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AssignManagerRequest(
        UUID employeeId,
        UUID managerId,
        Boolean isPrimary,
        LocalDate effectiveFrom
) {}