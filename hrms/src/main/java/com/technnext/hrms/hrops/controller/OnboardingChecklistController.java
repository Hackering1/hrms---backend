package com.technnext.hrms.hrops.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.hrops.dto.CompleteTaskRequest;
import com.technnext.hrms.hrops.entity.OnboardingChecklist;
import com.technnext.hrms.hrops.service.OnboardingChecklistService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboarding-tasks")
@RequiredArgsConstructor
public class OnboardingChecklistController {

    private final OnboardingChecklistService service;
    private final CurrentUserService currentUser;

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<OnboardingChecklist>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<OnboardingChecklist> create(@RequestBody OnboardingChecklist body) {
        return ApiResponse.ok("Task added", service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<OnboardingChecklist> update(@PathVariable Integer id, @RequestBody OnboardingChecklist body) {
        return ApiResponse.ok("Task updated", service.update(id, body));
    }

    /** Mark a task complete/incomplete. A MANAGER may only do this for their own team. */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<OnboardingChecklist> complete(
            @PathVariable Integer id,
            @RequestBody CompleteTaskRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, service.getById(id).getEmployeeId());
        return ApiResponse.ok("Task updated", service.setCompleted(id, body.completed(), body.completedBy()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }
}