package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.dto.PayrollAdjustmentRequest;
import com.technnext.hrms.payroll.entity.PayrollAdjustment;
import com.technnext.hrms.payroll.service.PayrollAdjustmentService;
import com.technnext.hrms.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll/adjustments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PayrollAdjustmentController {

    private final PayrollAdjustmentService service;

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<PayrollAdjustment>> byEmployeeAndMonth(
            @PathVariable UUID employeeId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.ok(service.byEmployeeAndMonth(employeeId, year, month));
    }

    @PostMapping
    public ApiResponse<PayrollAdjustment> create(@Valid @RequestBody PayrollAdjustmentRequest body, @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Adjustment added", service.create(body, principal.getUser().getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}