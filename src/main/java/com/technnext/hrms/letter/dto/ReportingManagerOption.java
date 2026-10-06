package com.technnext.hrms.letter.dto;

import java.util.UUID;

/**
 * Slim projection of a portal manager for the Generate Letter "Reporting
 * Manager" dropdown. Deliberately carries only what the dropdown needs
 * (id, display name, employee code, designation) — not the full
 * EmployeeResponse, which also holds Aadhaar/PAN/bank details.
 */
public record ReportingManagerOption(
        UUID id,
        String name,
        String employeeCode,
        String designation
) {}