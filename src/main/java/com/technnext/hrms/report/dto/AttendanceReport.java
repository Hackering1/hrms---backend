package com.technnext.hrms.report.dto;

import java.time.LocalDate;
import java.util.Map;

public record AttendanceReport(
        LocalDate date,
        long totalRecords,
        Map<String, Long> byStatus,
        long checkedIn,
        long checkedOut,
        long present,
        long absent,
        long regularized
) {}