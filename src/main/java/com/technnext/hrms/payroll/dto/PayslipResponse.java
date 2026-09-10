package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayslipResponse(
        Integer id,
        Integer payrollRunId,
        Integer month,
        Integer year,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        BigDecimal workingDays,
        BigDecimal paidDays,
        BigDecimal lopDays,
        BigDecimal grossEarnings,
        BigDecimal totalDeductions,
        BigDecimal netPay,
        BigDecimal employerCost,
        BigDecimal pfEmployee,
        BigDecimal pfEmployer,
        BigDecimal ptAmount,
        BigDecimal tdsAmount,
        String status,
        List<Line> earnings,
        List<Line> deductions,
        List<Line> employerContributions
) {
    public record Line(String name, BigDecimal amount) {}
}