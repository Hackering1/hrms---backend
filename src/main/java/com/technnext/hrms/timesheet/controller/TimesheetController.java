package com.technnext.hrms.timesheet.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import com.technnext.hrms.timesheet.dto.ProjectRequest;
import com.technnext.hrms.timesheet.dto.TimesheetDecision;
import com.technnext.hrms.timesheet.dto.TimesheetResponse;
import com.technnext.hrms.timesheet.dto.TimesheetSaveRequest;
import com.technnext.hrms.timesheet.entity.TimesheetProject;
import com.technnext.hrms.timesheet.service.TimesheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService service;
    private final CurrentUserService currentUser;

    // ── Projects ────────────────────────────────────────────────────────────────

    /** Active projects — what an employee can log time against. */
    @GetMapping("/projects")
    public ApiResponse<List<TimesheetProject>> activeProjects() {
        return ApiResponse.ok(service.listProjects(true));
    }

    @GetMapping("/projects/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<TimesheetProject>> allProjects() {
        return ApiResponse.ok(service.listProjects(false));
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<TimesheetProject> createProject(@RequestBody ProjectRequest body) {
        return ApiResponse.ok("Project created", service.createProject(body));
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<TimesheetProject> updateProject(@PathVariable Integer id, @RequestBody ProjectRequest body) {
        return ApiResponse.ok("Project updated", service.updateProject(id, body));
    }

    // ── My timesheet (always the caller's own) ──────────────────────────────────

    @GetMapping("/me")
    public ApiResponse<List<TimesheetResponse>> myHistory(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.history(currentUser.ownEmployeeId(principal)));
    }

    @GetMapping("/me/week/{weekStart}")
    public ApiResponse<TimesheetResponse> myWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok(service.getWeek(currentUser.ownEmployeeId(principal), weekStart));
    }

    @PutMapping("/me/week/{weekStart}")
    public ApiResponse<TimesheetResponse> saveMyWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestBody TimesheetSaveRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Timesheet saved",
                service.saveWeek(currentUser.ownEmployeeId(principal), weekStart, body));
    }

    @PostMapping("/me/week/{weekStart}/submit")
    public ApiResponse<TimesheetResponse> submitMyWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Timesheet submitted",
                service.submitWeek(currentUser.ownEmployeeId(principal), weekStart));
    }

    // ── Approvals (manager / HR, scoped to their reports) ───────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<TimesheetResponse>> pending(@AuthenticationPrincipal CustomUserDetails principal) {
        List<TimesheetResponse> all = service.pending();
        if (currentUser.isSuperAdmin(principal)) return ApiResponse.ok(all);
        Set<UUID> scope = currentUser.accessibleEmployeeIds(principal);
        return ApiResponse.ok(all.stream().filter(t -> scope.contains(t.employeeId())).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<TimesheetResponse> getById(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        TimesheetResponse t = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, t.employeeId());
        return ApiResponse.ok(t);
    }

    @PutMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<TimesheetResponse> decide(
            @PathVariable Integer id,
            @RequestBody TimesheetDecision body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        TimesheetResponse existing = service.getById(id);
        currentUser.assertCanAccessEmployee(principal, existing.employeeId());
        // reviewed_by references employees(id); a profile-less admin account stores null.
        UUID reviewer = currentUser.ownEmployeeIdOrEmpty(principal).orElse(null);
        return ApiResponse.ok("Decision recorded", service.decide(id, reviewer, body));
    }
}