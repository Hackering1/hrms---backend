package com.technnext.hrms.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BulkAttendanceRequest(
        LocalDate date,
        String status,
        String remarks,
        List<UUID> employeeIds
) {}