package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.dto.PayrollRunRequest;
import com.technnext.hrms.payroll.dto.PayrollRunResponse;
import com.technnext.hrms.payroll.service.PayrollExcelExportService;
import com.technnext.hrms.payroll.service.PayrollRunService;
import com.technnext.hrms.security.CurrentUserService;
import com.technnext.hrms.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// The entire payroll RUN lifecycle (process/approve/pay/cancel) is SUPER_ADMIN + HR_ADMIN
// only — this is money leaving the company, not routine master data.
@RestController
@RequestMapping("/api/payroll/runs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class PayrollRunController {

    private final PayrollRunService service;
    private final PayrollExcelExportService excelExportService;
    private final CurrentUserService currentUser;

    @GetMapping
    public ApiResponse<List<PayrollRunResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<PayrollRunResponse> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping("/process")
    public ApiResponse<PayrollRunResponse> process(@Valid @RequestBody PayrollRunRequest body, @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Payroll processed", service.process(body.month(), body.year(), principal.getUser().getId()));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<PayrollRunResponse> approve(@PathVariable Integer id, @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Payroll run approved", service.approve(id, principal.getUser().getId()));
    }

    @PutMapping("/{id}/mark-paid")
    public ApiResponse<PayrollRunResponse> markPaid(@PathVariable Integer id) {
        return ApiResponse.ok("Payroll run marked as paid", service.markPaid(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Integer id) {
        service.cancel(id);
        return ApiResponse.ok("Payroll run cancelled", null);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Integer id) {
        byte[] xlsx = excelExportService.exportRun(id);
        PayrollRunResponse run = service.getById(id);
        String filename = "Salary_Register_" + run.month() + "_" + run.year() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(xlsx.length);
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}