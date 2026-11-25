package com.technnext.hrms.suite.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PayrollRunRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") String period,
        @NotEmpty @Size(max = 500) List<@Valid PayrollRunEmployeeInput> employees) { }
