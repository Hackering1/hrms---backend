package com.technnext.hrms.report.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.report.dto.AttendanceReport;
import com.technnext.hrms.report.dto.EmployeeReport;
import com.technnext.hrms.report.dto.LeaveReport;
import com.technnext.hrms.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
public class ReportController {

    private final ReportService service;

    @GetMapping("/employees")
    public ApiResponse<EmployeeReport> employees() {
        return ApiResponse.ok(service.employeeReport());
    }

    @GetMapping("/attendance")
    public ApiResponse<AttendanceReport> attendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.attendanceReport(date));
    }

    @GetMapping("/leave")
    public ApiResponse<LeaveReport> leave() {
        return ApiResponse.ok(service.leaveReport());
    }
}