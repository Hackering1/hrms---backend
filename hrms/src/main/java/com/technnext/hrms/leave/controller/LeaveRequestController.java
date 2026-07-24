package com.technnext.hrms.leave.controller;

import com.technnext.hrms.audit.service.AuditService;
import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.leave.dto.LeaveApproval;
import com.technnext.hrms.leave.dto.LeaveRegularizeRequest;
import com.technnext.hrms.leave.dto.LeaveRequestCreate;
import com.technnext.hrms.leave.dto.LeaveRequestResponse;
import com.technnext.hrms.leave.service.LeaveRequestService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService service;
    private final CurrentUserService currentUser;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<List<LeaveRequestResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (currentUser.isSuperAdmin(principal)) {
            return ApiResponse.ok(service.getAll());
        }
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(
                service.getAll().stream()
                        .filter(r -> scope.contains(r.employeeId()))
                        .toList());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<LeaveRequestResponse>> getPending(
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (currentUser.isSuperAdmin(principal)) {
            return ApiResponse.ok(service.getPending());
        }
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(
                service.getPending().stream()
                        .filter(r -> scope.contains(r.employeeId()))
                        .toList());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<LeaveRequestResponse>> getByEmployee(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.getByEmployee(employeeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<LeaveRequestResponse> getById(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LeaveRequestResponse r = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, r.employeeId());
        return ApiResponse.ok(r);
    }

    @PostMapping
    public ApiResponse<LeaveRequestResponse> apply(
            @RequestBody LeaveRequestCreate body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID actingId = currentUser.resolveActingEmployeeId(principal, body.employeeId());
        LeaveRequestCreate safe = new LeaveRequestCreate(
                actingId, body.leaveTypeId(), body.fromDate(), body.toDate(),
                body.numberOfDays(), body.dayType(), body.reason(), body.documentUrl());
        LeaveRequestResponse created = service.apply(safe);

        // If a privileged user applied on behalf of someone else, that's a
        // regularization (back-dated / on-behalf entry) worth auditing.
        if (!actingId.equals(principal.getUser().getId())) {
            auditService.record(principal.getUser().getId(),
                    "BACKDATE_LEAVE", "LEAVE", String.valueOf(created.id()),
                    null, snapshot(created));
        }
        return ApiResponse.ok("Leave applied", created);
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('MANAGER','HR_ADMIN','HR_EXECUTIVE','SUPER_ADMIN')")
    public ApiResponse<LeaveRequestResponse> decide(
            @PathVariable Integer id,
            @RequestBody LeaveApproval body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LeaveRequestResponse existing = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, existing.employeeId());

        // approved_by references the EMPLOYEES table. Use the reviewer's employee
        // id when they have one; leave it null for a profile-less approver (e.g. a
        // Super Admin / CEO system account) so the FK constraint is satisfied and
        // approvals don't 500.
        UUID reviewerEmployeeId = currentUser.ownEmployeeIdOrEmpty(principal).orElse(null);

        LeaveApproval safe = new LeaveApproval(reviewerEmployeeId, body.status(), body.approverRemarks());
        return ApiResponse.ok("Decision recorded", service.decide(id, safe));
    }

    /**
     * HR LEAVE REGULARIZATION — edit an existing leave. Audited.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<LeaveRequestResponse> regularize(
            @PathVariable Integer id,
            @RequestBody LeaveRegularizeRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LeaveRequestResponse before = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, before.employeeId());

        LeaveRequestResponse after = service.regularize(id, body);

        auditService.record(principal.getUser().getId(),
                "EDIT_LEAVE", "LEAVE", String.valueOf(id),
                snapshot(before), snapshot(after));

        return ApiResponse.ok("Leave updated", after);
    }

    /**
     * HR LEAVE REGULARIZATION — cancel a leave (returns days to balance). Audited.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<LeaveRequestResponse> cancel(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LeaveRequestResponse before = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, before.employeeId());

        LeaveRequestResponse after = service.cancel(id);

        auditService.record(principal.getUser().getId(),
                "CANCEL_LEAVE", "LEAVE", String.valueOf(id),
                snapshot(before), snapshot(after));

        return ApiResponse.ok("Leave cancelled", after);
    }

    /**
     * HR/Admin only — permanently remove a leave request row (any status),
     * reversing its balance effect first if it was still PENDING/APPROVED.
     * Distinct from cancel() above, which only flips the status to CANCELLED
     * and left no way to actually clear out old/cancelled rows.
     */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE')")
    public ApiResponse<Void> deletePermanent(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LeaveRequestResponse before = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, before.employeeId());

        service.deletePermanent(id);

        auditService.record(principal.getUser().getId(),
                "DELETE_LEAVE", "LEAVE", String.valueOf(id),
                snapshot(before), null);

        return ApiResponse.ok("Leave permanently deleted", null);
    }

    /** Compact snapshot of the fields that matter for an audit diff. */
    private Map<String, Object> snapshot(LeaveRequestResponse r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("employeeId", r.employeeId());
        m.put("leaveTypeId", r.leaveTypeId());
        m.put("fromDate", r.fromDate());
        m.put("toDate", r.toDate());
        m.put("numberOfDays", r.numberOfDays());
        m.put("status", r.status());
        return m;
    }
}
