package com.technnext.hrms.hrops.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.hrops.dto.ProbationReview;
import com.technnext.hrms.hrops.entity.ProbationTracking;
import com.technnext.hrms.hrops.service.ProbationTrackingService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/probation")
@RequiredArgsConstructor
public class ProbationTrackingController {

    private final ProbationTrackingService service;
    private final CurrentUserService currentUser;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<List<ProbationTracking>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<ProbationTracking>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<ProbationTracking> create(@RequestBody ProbationTracking body) {
        return ApiResponse.ok("Probation created", service.create(body));
    }

    /** Review a probation record. A MANAGER may only do this for their own team. */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<ProbationTracking> review(
            @PathVariable Integer id,
            @RequestBody ProbationReview body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, service.getById(id).getEmployeeId());
        return ApiResponse.ok("Probation reviewed",
                service.review(id, body.status(), body.reviewNotes(), body.reviewedBy()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}