package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.payroll.dto.EmployeeSalaryAssignRequest;
import com.technnext.hrms.payroll.dto.EmployeeSalaryResponse;
import com.technnext.hrms.payroll.service.EmployeeSalaryService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll/employee-salaries")
@RequiredArgsConstructor
public class EmployeeSalaryController {

    private final EmployeeSalaryService service;
    private final CurrentUserService currentUser;

    // History/current for one employee — HR/Admin can view anyone; an employee can view their own.
    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<EmployeeSalaryResponse>> history(
            @PathVariable UUID employeeId, @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.history(employeeId));
    }

    @GetMapping("/employee/{employeeId}/current")
    public ApiResponse<EmployeeSalaryResponse> current(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) String asOf,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        LocalDate date = asOf != null ? LocalDate.parse(asOf) : LocalDate.now();
        Optional<EmployeeSalaryResponse> result = service.currentActive(employeeId, date);
        return ApiResponse.ok(result.orElse(null));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<EmployeeSalaryResponse> assign(@Valid @RequestBody EmployeeSalaryAssignRequest body) {
        if (body.annualCtc() == null || body.annualCtc().signum() <= 0) {
            throw new BadRequestException("annualCtc must be a positive amount.");
        }
        return ApiResponse.ok("Salary assigned", service.assign(body));
    }
}