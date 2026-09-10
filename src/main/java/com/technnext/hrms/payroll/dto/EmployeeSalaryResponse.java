package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmployeeSalaryResponse(
        Integer id,
        UUID employeeId,
        String employeeCode,
        String employeeName,
        Integer salaryStructureId,
        String salaryStructureName,
        BigDecimal annualCtc,
        BigDecimal monthlyGross,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String taxRegime,
        Boolean pfApplicable,
        Boolean ptApplicable,
        BigDecimal tdsOverrideMonthly,
        List<ComponentBreakup> components
) {
    public record ComponentBreakup(
            String componentName,
            String componentType,
            BigDecimal monthlyAmount,
            BigDecimal annualAmount
    ) {}
}