package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.payroll.dto.PayslipResponse;
import com.technnext.hrms.payroll.service.PayslipService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll/payslips")
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService service;
    private final CurrentUserService currentUser;

    @GetMapping("/run/{payrollRunId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<List<PayslipResponse>> byRun(@PathVariable Integer payrollRunId) {
        return ApiResponse.ok(service.byRun(payrollRunId));
    }

    // An employee can see their own payslip history; HR/Admin can see anyone's.
    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<PayslipResponse>> byEmployee(@PathVariable UUID employeeId, @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.byEmployee(employeeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PayslipResponse> getById(@PathVariable Integer id, @AuthenticationPrincipal CustomUserDetails principal) {
        PayslipResponse slip = service.getById(id);
        if (slip.employeeId() == null || !currentUser.canAccessEmployee(principal, slip.employeeId())) {
            throw new BadRequestException("You do not have access to this payslip.");
        }
        return ApiResponse.ok(slip);
    }

    // HR-only: manually re-send the payslip email (e.g. the automatic send after
    // "Mark Paid" failed, or the employee's email address was just corrected).
    @PostMapping("/{id}/resend-email")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> resendEmail(@PathVariable Integer id) {
        service.resendEmail(id);
        return ApiResponse.ok("Payslip email queued for resend", null);
    }
}