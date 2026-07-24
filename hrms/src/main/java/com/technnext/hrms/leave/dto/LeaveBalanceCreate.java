package com.technnext.hrms.leave.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaveBalanceCreate(
        UUID employeeId,
        Integer leaveTypeId,
        Integer year,
        BigDecimal allocatedDays,
        BigDecimal carriedDays
) {}