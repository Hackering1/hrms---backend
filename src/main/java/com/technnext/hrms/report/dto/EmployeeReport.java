package com.technnext.hrms.report.dto;

import java.util.Map;

public record EmployeeReport(
        long totalEmployees,
        Map<String, Long> byStatus,
        Map<String, Long> byDepartment,
        Map<String, Long> byBranch,
        Map<String, Long> byEmploymentType
) {}