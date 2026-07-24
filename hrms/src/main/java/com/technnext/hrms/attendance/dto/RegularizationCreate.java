package com.technnext.hrms.attendance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RegularizationCreate(
        UUID employeeId,
        LocalDate attendanceDate,
        LocalTime requestedIn,
        LocalTime requestedOut,
        String reason
) {}