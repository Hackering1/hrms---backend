package com.technnext.hrms.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveRequestCreate(
        UUID employeeId,
        Integer leaveTypeId,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal numberOfDays,
        String dayType,
        String reason,
        String documentUrl
) {}