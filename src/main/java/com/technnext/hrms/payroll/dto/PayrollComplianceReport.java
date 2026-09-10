package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayrollComplianceReport(
        Integer payrollRunId,
        Integer month,
        Integer year,
        BigDecimal totalPfEmployee,
        BigDecimal totalPfEmployer,
        BigDecimal totalPt,
        BigDecimal totalTds,
        List<Row> rows
) {
    public record Row(
            UUID employeeId,
            String employeeCode,
            String employeeName,
            BigDecimal grossEarnings,
            BigDecimal pfEmployee,
            BigDecimal pfEmployer,
            BigDecimal ptAmount,
            BigDecimal tdsAmount,
            BigDecimal netPay
    ) {}
}