package com.technnext.hrms.manager.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.employee.dto.EmployeeResponse;
import com.technnext.hrms.employee.entity.EmployeeManager;
import com.technnext.hrms.manager.dto.AssignManagerRequest;
import com.technnext.hrms.manager.dto.ManagerTeamSize;
import com.technnext.hrms.manager.service.ManagerService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerService service;
    private final CurrentUserService currentUser;

    /**
     * A manager's team. A manager may only read their OWN team; Super Admin may
     * read anyone's. (Closes the hole where any caller could read any team.)
     */
    @GetMapping("/{managerId}/team")
    public ApiResponse<List<EmployeeResponse>> getTeam(
            @PathVariable UUID managerId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, managerId);
        return ApiResponse.ok(service.getTeam(managerId));
    }

    @GetMapping("/{managerId}/team-ids")
    public ApiResponse<List<UUID>> getTeamIds(
            @PathVariable UUID managerId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, managerId);
        return ApiResponse.ok(service.getTeamIds(managerId));
    }

    // Managers who already have reports.
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MANAGER','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<List<EmployeeResponse>> getAllManagers() {
        return ApiResponse.ok(service.getAllManagers());
    }

    // Every employee eligible to be a manager (has a MANAGER/HR login role).
    @GetMapping("/assignable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<EmployeeResponse>> getAssignableManagers() {
        return ApiResponse.ok(service.getAssignableManagers());
    }

    // All assignment rows (id + employeeId + managerId) for the admin screen.
    @GetMapping("/assignments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<EmployeeManager>> getAllAssignments() {
        return ApiResponse.ok(service.getAllAssignments());
    }

    // NEW: org-wide employees-per-manager breakdown (Super Admin analytics).
    @GetMapping("/team-sizes")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<ManagerTeamSize>> getTeamSizes() {
        return ApiResponse.ok(service.getTeamSizes());
    }

    @GetMapping("/employee/{employeeId}/managers")
    public ApiResponse<List<EmployeeResponse>> getManagers(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getManagers(employeeId));
    }

    // Assigning teams is a Super Admin action (you confirmed only Super Admin
    // creates/assigns employees).
    @PostMapping("/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<EmployeeManager> assign(@RequestBody AssignManagerRequest body) {
        return ApiResponse.ok("Manager assigned", service.assign(body));
    }

    @DeleteMapping("/assign/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> unassign(@PathVariable Integer id) {
        service.unassign(id);
        return ApiResponse.ok("Unassigned", null);
    }
}