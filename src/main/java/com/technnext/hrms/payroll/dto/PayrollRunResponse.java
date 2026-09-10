package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayrollRunResponse(
        Integer id,
        Integer month,
        Integer year,
        String status,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet,
        Integer employeeCount,
        LocalDateTime processedAt,
        LocalDateTime approvedAt,
        LocalDateTime paidAt,
        String remarks
) {}