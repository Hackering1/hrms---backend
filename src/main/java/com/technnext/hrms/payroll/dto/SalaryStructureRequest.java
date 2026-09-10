package com.technnext.hrms.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record SalaryStructureRequest(
        @NotBlank String name,
        String description,
        Boolean isActive,
        @NotEmpty List<ComponentLine> components
) {
    public record ComponentLine(
            Integer salaryComponentId,
            String calculationType,   // FLAT | PERCENT_OF_CTC | PERCENT_OF_BASIC | REMAINDER
            BigDecimal percentage,    // required for PERCENT_* types
            BigDecimal flatAmount,    // required for FLAT type
            Integer displayOrder
    ) {}
}