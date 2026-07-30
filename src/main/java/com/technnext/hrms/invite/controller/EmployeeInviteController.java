package com.technnext.hrms.invite.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.invite.dto.InviteEmployeeRequest;
import com.technnext.hrms.invite.entity.EmployeeInvite;
import com.technnext.hrms.invite.service.EmployeeInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Super-Admin-only. Managers and Employees must never reach these endpoints —
 * enforced both here (@PreAuthorize) and in the frontend (nav hidden + RoleRoute
 * guard), matching the existing defense-in-depth pattern used elsewhere.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmployeeInviteController {

    private final EmployeeInviteService inviteService;

    @PostMapping("/invite")
    public ApiResponse<EmployeeInvite> sendInvitation(@RequestBody InviteEmployeeRequest body) {
        return ApiResponse.ok("Invitation sent", inviteService.sendInvitation(body));
    }

    @GetMapping("/invitations")
    public ApiResponse<List<EmployeeInvite>> listInvitations() {
        return ApiResponse.ok(inviteService.listInvites());
    }

    @PostMapping("/invitations/{id}/resend")
    public ApiResponse<EmployeeInvite> resend(@PathVariable UUID id) {
        return ApiResponse.ok("Invitation resent", inviteService.resendInvitation(id));
    }

    @DeleteMapping("/invitations/{id}")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        inviteService.cancelInvitation(id);
        return ApiResponse.ok("Invitation cancelled", null);
    }

    @GetMapping("/pending")
    public ApiResponse<List<com.technnext.hrms.employee.dto.EmployeeResponse>> pendingProfiles() {
        return ApiResponse.ok(inviteService.listPendingProfiles());
    }
}