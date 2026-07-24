package com.technnext.hrms.leave.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.leave.dto.LeaveBalanceCreate;
import com.technnext.hrms.leave.entity.LeaveBalance;
import com.technnext.hrms.leave.service.LeaveBalanceService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService service;
    private final CurrentUserService currentUser;

    @GetMapping("/employee/{employeeId}/year/{year}")
    public ApiResponse<List<LeaveBalance>> getByEmployeeAndYear(
            @PathVariable UUID employeeId, @PathVariable Integer year,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployeeAndYear(employeeId, year));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<LeaveBalance> allocate(@RequestBody LeaveBalanceCreate body) {
        return ApiResponse.ok("Allocated", service.allocate(body));
    }
}