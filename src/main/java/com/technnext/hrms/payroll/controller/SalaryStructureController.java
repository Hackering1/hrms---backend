package com.technnext.hrms.payroll.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.payroll.dto.SalaryStructureRequest;
import com.technnext.hrms.payroll.dto.SalaryStructureResponse;
import com.technnext.hrms.payroll.service.SalaryStructureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payroll/salary-structures")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
public class SalaryStructureController {

    private final SalaryStructureService service;

    @GetMapping
    public ApiResponse<List<SalaryStructureResponse>> getAll() {
        return ApiResponse.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SalaryStructureResponse> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<SalaryStructureResponse> create(@Valid @RequestBody SalaryStructureRequest body) {
        return ApiResponse.ok("Created", service.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<SalaryStructureResponse> update(@PathVariable Integer id, @Valid @RequestBody SalaryStructureRequest body) {
        return ApiResponse.ok("Updated", service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deactivated", null);
    }
}