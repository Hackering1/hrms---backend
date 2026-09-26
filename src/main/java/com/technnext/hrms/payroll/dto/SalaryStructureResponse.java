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
            Integer displayOrder,
            // Only set (non-null) for the component with code "BASIC": true when its
            // percentage is below the 50% wage-code floor (Basic, or Basic+DA where a
            // separate DA component exists, must be >= 50% of the total remuneration).
            // Informational only — does not block save; the UI renders it as a badge.
            Boolean belowBasicFloor
    ) {}
}