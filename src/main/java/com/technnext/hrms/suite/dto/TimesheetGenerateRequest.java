package com.technnext.hrms.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record TimesheetGenerateRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-W(0[1-9]|[1-4][0-9]|5[0-3])") String period,
        UUID employeeId) { }
