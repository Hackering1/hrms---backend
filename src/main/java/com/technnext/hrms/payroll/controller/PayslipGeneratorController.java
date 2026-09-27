package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.dto.PayslipResponse;
import com.technnext.hrms.payroll.service.PayslipGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Payroll → Payslip Generator: lets HR/Admin generate a single employee's
 * payslip inside an existing, already-processed Payroll Run — for the case
 * where the bulk run missed them (e.g. added to payroll afterwards).
 *
 * Deliberately a separate controller/service pair from
 * PayrollRunController/PayrollRunService, which remain unmodified and still
 * own the full run lifecycle (process/approve/mark-paid/cancel).
 */
@RestController
@RequestMapping("/api/payroll/payslip-generator")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") // same rule as every other /api/payroll/runs/** admin action
public class PayslipGeneratorController {

    private final PayslipGeneratorService service;

    @PostMapping("/runs/{runId}/employees/{employeeId}")
    public ApiResponse<PayslipResponse> generate(
            @PathVariable Integer runId,
            @PathVariable UUID employeeId) {
        return ApiResponse.ok("Payslip generated", service.generateForEmployee(runId, employeeId));
    }
}