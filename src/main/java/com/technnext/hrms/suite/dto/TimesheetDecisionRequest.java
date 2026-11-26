package com.technnext.hrms.suite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TimesheetDecisionRequest(
        @NotBlank String status,
        @Size(max = 2000) String remarks) { }
