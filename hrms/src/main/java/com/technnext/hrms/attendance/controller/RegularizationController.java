package com.technnext.hrms.attendance.controller;

import com.technnext.hrms.attendance.dto.RegularizationCreate;
import com.technnext.hrms.attendance.dto.RegularizationDecision;
import com.technnext.hrms.attendance.entity.AttendanceRegularization;
import com.technnext.hrms.attendance.service.RegularizationService;
import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Attendance regularizations, scoped by role (manager-routed approvals):
 *  - Super Admin: everyone.
 *  - Manager: only their own team (+ themselves).
 *  - Employee: only their own.
 * A manager can only DECIDE requests from their own team; HR/Super Admin unrestricted.
 * Reviewer id always comes from the JWT, never the request body.
 */
@RestController
@RequestMapping("/api/attendance-regularizations")
@RequiredArgsConstructor
public class RegularizationController {

    private final RegularizationService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public ApiResponse<List<AttendanceRegularization>> getAll(
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (currentUser.isSuperAdmin(principal)) {
            return ApiResponse.ok(service.getAll());
        }
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(
                service.getAll().stream()
                        .filter(r -> scope.contains(r.getEmployeeId()))
                        .toList());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<AttendanceRegularization>> getPending(
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (currentUser.isSuperAdmin(principal)) {
            return ApiResponse.ok(service.getPending());
        }
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(
                service.getPending().stream()
                        .filter(r -> scope.contains(r.getEmployeeId()))
                        .toList());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<AttendanceRegularization>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @PostMapping
    public ApiResponse<AttendanceRegularization> create(
            @RequestBody RegularizationCreate body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // SECURITY: pin the request to the caller's own employee id (privileged
        // roles may raise on behalf of a team member via the supplied id).
        UUID actingId = currentUser.resolveActingEmployeeId(principal, body.employeeId());
        RegularizationCreate safe = new RegularizationCreate(
                actingId, body.attendanceDate(), body.requestedIn(),
                body.requestedOut(), body.reason());
        return ApiResponse.ok("Regularization submitted", service.create(safe));
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<AttendanceRegularization> decide(
            @PathVariable Integer id,
            @RequestBody RegularizationDecision body,
            @AuthenticationPrincipal CustomUserDetails principal) {

        // Manager can only decide their own team's requests; HR/admin unrestricted.
        AttendanceRegularization existing = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, existing.getEmployeeId());

        // reviewer id from the JWT, never the client body.
        UUID reviewerId = principal.getUser().getId();
        RegularizationDecision safeDecision = new RegularizationDecision(
                reviewerId, body.status(), body.reviewerRemarks());
        return ApiResponse.ok("Decision recorded", service.decide(id, safeDecision));
    }

    @DeleteMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // Employees cancel their own PENDING request; service verifies ownership.
        UUID employeeId = currentUser.ownEmployeeId(principal);
        service.cancel(id, employeeId);
        return ApiResponse.ok("Request cancelled", null);
    }

    /**
     * HR/Admin only — permanently remove a regularization request row (any
     * status). Distinct from cancel() above, which only flips PENDING ->
     * CANCELLED and is available to the requesting employee themselves.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<Void> deletePermanent(@PathVariable Integer id) {
        service.deletePermanent(id);
        return ApiResponse.ok("Request deleted", null);
    }
}
