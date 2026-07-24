package com.technnext.hrms.hrops.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.hrops.entity.ConfirmationRecord;
import com.technnext.hrms.hrops.service.ConfirmationRecordService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/confirmations")
@RequiredArgsConstructor
public class ConfirmationRecordController {

    private final ConfirmationRecordService service;
    private final CurrentUserService currentUser;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<List<ConfirmationRecord>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<ConfirmationRecord>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<ConfirmationRecord> create(@RequestBody ConfirmationRecord body) {
        return ApiResponse.ok("Confirmation recorded", service.create(body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}