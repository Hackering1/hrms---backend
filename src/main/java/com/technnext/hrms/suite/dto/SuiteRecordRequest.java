package com.technnext.hrms.suite.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SuiteRecordRequest(
        @NotBlank @Size(max = 180) String title,
        UUID employeeId,
        @Size(max = 24) String status,
        @Size(max = 16) String period,
        @DecimalMin("0.00") BigDecimal amount,
        @Min(0) Integer minutes,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 8000) String details,
        Boolean consent) { }
