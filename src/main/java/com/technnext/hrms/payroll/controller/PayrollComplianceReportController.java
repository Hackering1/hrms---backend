package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.dto.PayrollComplianceReport;
import com.technnext.hrms.payroll.service.PayrollComplianceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payroll/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PayrollComplianceReportController {

    private final PayrollComplianceReportService service;

    @GetMapping("/run/{payrollRunId}/compliance")
    public ApiResponse<PayrollComplianceReport> compliance(@PathVariable Integer payrollRunId) {
        return ApiResponse.ok(service.forRun(payrollRunId));
    }
}