package com.technnext.hrms.payroll.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalaryStructureResponse(
        Integer id,
        String name,
        String description,
        Boolean isActive,
        List<ComponentLine> components
) {
    public record ComponentLine(
            Integer salaryComponentId,
            String componentName,
            String componentCode,
            String componentType,     // EARNING | DEDUCTION | EMPLOYER_CONTRIBUTION
            String calculationType,
            BigDecimal percentage,
            BigDecimal flatAmount,
            Boolean isStatutory,
            Integer displayOrder
    ) {}
}