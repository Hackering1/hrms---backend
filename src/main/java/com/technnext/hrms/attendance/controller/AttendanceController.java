package com.technnext.hrms.attendance.controller;

import com.technnext.hrms.attendance.dto.CheckRequest;
import com.technnext.hrms.attendance.entity.Attendance;
import com.technnext.hrms.attendance.service.AttendanceService;
import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.attendance.dto.BulkAttendanceRequest;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SECURITY (Phase 1): every self-service action now derives the employee from the
 * authenticated user, not from a client-supplied employeeId. A plain employee can
 * only check in / out / view attendance as themselves; privileged roles may act
 * for others. This closes the horizontal-access (IDOR) hole.
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;
    private final CurrentUserService currentUser;

    @PostMapping("/check-in")
    public ApiResponse<Attendance> checkIn(
            @RequestBody CheckRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        CheckRequest safe = withActingEmployee(body, principal);
        return ApiResponse.ok("Checked in", service.checkIn(safe));
    }

    @PostMapping("/check-out")
    public ApiResponse<Attendance> checkOut(
            @RequestBody CheckRequest body,
            @AuthenticationPrincipal CustomUserDetails principal) {
        CheckRequest safe = withActingEmployee(body, principal);
        return ApiResponse.ok("Checked out", service.checkOut(safe));
    }

    // Admin only: mark attendance for many employees at once.
    @PostMapping("/bulk")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Integer> bulkMark(@RequestBody BulkAttendanceRequest body) {
        int n = service.bulkMark(body);
        return ApiResponse.ok(n + " attendance record(s) marked", n);
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<Attendance>> history(
            @PathVariable UUID employeeId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // Employees may only read their own history; privileged roles may read anyone.
        currentUser.assertCanAccessEmployee(principal, employeeId);
        return ApiResponse.ok(service.history(employeeId));
    }

    // Whole-day roster is management data — privileged roles only.
    @GetMapping("/date/{date}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<List<Attendance>> forDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.forDate(date));
    }

    /** Rebuild the CheckRequest with the caller's resolved employee id. */
    private CheckRequest withActingEmployee(CheckRequest body, CustomUserDetails principal) {
        UUID actingId = currentUser.resolveActingEmployeeId(principal, body.employeeId());
        return new CheckRequest(
                actingId,
                body.ipAddress(),
                body.deviceInfo(),
                body.latitude(),
                body.longitude(),
                body.checkInPhotoId(),
                body.checkOutLatitude(),
                body.checkOutLongitude(),
                body.checkOutPhotoId());
    }
}