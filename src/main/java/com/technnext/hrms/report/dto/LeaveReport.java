package com.technnext.hrms.report.dto;

import java.math.BigDecimal;
import java.util.Map;

public record LeaveReport(
        long totalRequests,
        Map<String, Long> byStatus,
        BigDecimal totalDaysRequested,
        BigDecimal totalDaysApproved
) {}